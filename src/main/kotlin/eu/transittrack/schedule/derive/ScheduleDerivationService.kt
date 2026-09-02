package eu.transittrack.schedule.derive

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper

import eu.transittrack.Extent
import eu.transittrack.Point
import eu.transittrack.Polyline
import eu.transittrack.gtfs.ingest.IngestionFailedEvent
import eu.transittrack.gtfs.ingest.IngestionPostProcessor
import eu.transittrack.gtfs.model.Frequency
import eu.transittrack.gtfs.model.FrequencyRepository
import eu.transittrack.gtfs.model.RouteRepository
import eu.transittrack.gtfs.model.ShapePointRepository
import eu.transittrack.gtfs.model.StopRepository
import eu.transittrack.gtfs.model.StopTime
import eu.transittrack.gtfs.model.StopTimeRepository
import eu.transittrack.gtfs.model.Trip
import eu.transittrack.gtfs.model.TripRepository
import eu.transittrack.haversineMeters
import eu.transittrack.median
import eu.transittrack.schedule.ScheduleProperties
import eu.transittrack.schedule.model.Block
import eu.transittrack.schedule.model.BlockTrip
import eu.transittrack.schedule.model.SchedTrip
import eu.transittrack.schedule.model.ScheduleTime
import eu.transittrack.schedule.model.StopPath
import eu.transittrack.schedule.model.TripPattern

/**
 * Derives the schedule model for one GTFS revision: trip patterns, stop paths, schedule times per
 * trip, plus the pattern-aggregate and block passes (typical travel/dwell times, block ordering,
 * layovers). Owns its failure cleanup: any throwable wipes the revision's derived rows and
 * rethrows.
 */
@Service
@ConditionalOnProperty(name = ["transittrack.schedule.enabled"], havingValue = "true")
class ScheduleDerivationService(
    private val stops: StopRepository,
    private val routes: RouteRepository,
    private val trips: TripRepository,
    private val stopTimes: StopTimeRepository,
    private val shapePoints: ShapePointRepository,
    private val frequencies: FrequencyRepository,
    private val writer: ScheduleWriter,
    private val derivedGtfsWriter: DerivedGtfsWriter,
    private val props: ScheduleProperties,
    private val json: JsonMapper,
) : IngestionPostProcessor {
    private val log = LoggerFactory.getLogger(javaClass)

    /** Holds what the aggregate/block passes need without reloading entities. */
    class DerivedTrip(
        val schedTripId: Long,
        val patternId: Long,
        val blockId: String?,
        val serviceId: String,
        val routeId: String,
        val startSec: Int,
        val endSec: Int,
        val firstStopId: String,
        val lastStopId: String,
        val frequencyBased: Boolean,
        val resolved: List<ResolvedScheduleTime>,
    )

    /** Result carried out of [deriveCore] for reuse by the aggregate/block passes. */
    class CoreResult(
        val counts: MutableMap<String, Long>,
        val derivedTrips: List<DerivedTrip>,
        val patternStopPathIds: Map<Long, List<Long>>,
        val routeExtents: Map<String, Extent> = emptyMap(),
        var pendingPathUpdates: MutableMap<Long, StopPathAggregateUpdate> = mutableMapOf(),
    )

    private fun aggregatePass(core: CoreResult) {
        val byPattern = core.derivedTrips.groupBy { it.patternId }
        val pathUpdates = ArrayList<StopPathAggregateUpdate>()
        for ((patternId, tripsOfPattern) in byPattern) {
            val pathIds = core.patternStopPathIds[patternId] ?: continue
            for (idx in pathIds.indices) {
                val travels =
                    tripsOfPattern.mapNotNull {
                        it.resolved.getOrNull(idx)?.schedTravelTimeSec
                    }
                val dwells =
                    tripsOfPattern.mapNotNull {
                        it.resolved.getOrNull(idx)?.schedDwellTimeSec
                    }
                pathUpdates.add(
                    StopPathAggregateUpdate(
                        stopPathId = pathIds[idx],
                        typicalTravelTimeSec = median(travels),
                        typicalDwellTimeSec = median(dwells),
                        layoverStop = idx == 0, // block pass may flip the last index
                        breakTimeSec = null,
                    ),
                )
            }
        }
        // Only trip_count changes on trip_pattern here; build-time extent/length stay.
        writer.applyTripPatternTripCount(byPattern.mapValues { it.value.size })
        writer.applyStopPathAggregates(pathUpdates)
        core.pendingPathUpdates = pathUpdates.associateBy { it.stopPathId }.toMutableMap()
    }

    private fun blockPass(
        revisionId: Long,
        core: CoreResult,
    ) {
        // Frequency trips are normalized to startTimeSec = 0, so they would all sort to
        // the front of a block and produce nonsense layovers. They are not blocked.
        val blockInputs =
            core.derivedTrips
                .filter { it.blockId != null && !it.frequencyBased }
                .map {
                    BlockTripInput(
                        schedTripId = it.schedTripId,
                        blockId = it.blockId!!,
                        serviceId = it.serviceId,
                        routeId = it.routeId,
                        startTimeSec = it.startSec,
                        endTimeSec = it.endSec,
                        firstStopId = it.firstStopId,
                        lastStopId = it.lastStopId,
                    )
                }
        val results = BlockBuilder.build(blockInputs)
        val blockRows =
            results.map {
                Block(
                    revisionId = revisionId,
                    blockId = it.blockId,
                    serviceId = it.serviceId,
                    startTimeSec = it.startTimeSec,
                    endTimeSec = it.endTimeSec,
                    tripCount = it.tripCount,
                    routeIds = it.routeIds,
                )
            }
        writer.write(blockRows)
        core.counts["block"] = blockRows.size.toLong()

        val blockPkByKey = blockRows.associate { (it.blockId to it.serviceId) to it.id!! }
        val blockTripRows =
            results.flatMap { r ->
                val blockPk = blockPkByKey.getValue(r.blockId to r.serviceId)
                r.tripUpdates.map { u ->
                    BlockTrip(
                        revisionId = revisionId,
                        blockId = blockPk,
                        schedTripId = u.schedTripId,
                        listIndex = u.listIndex,
                        layoverAfterSec = u.layoverAfterSec,
                        deadheadAfter = u.deadheadAfter,
                    )
                }
            }
        writer.write(blockTripRows)

        // Per-pattern layover flag on the last stop path.
        val layoverByTrip =
            results
                .flatMap { it.tripUpdates }
                .filter { (it.layoverAfterSec ?: -1) >= props.layoverThresholdSec }
                .associate {
                    it.schedTripId to
                        it.layoverAfterSec!!
                }
        val byPattern = core.derivedTrips.groupBy { it.patternId }
        val lastPathUpdates = ArrayList<StopPathAggregateUpdate>()
        for ((patternId, tripsOfPattern) in byPattern) {
            val gaps = tripsOfPattern.mapNotNull { layoverByTrip[it.schedTripId] }
            if (gaps.isEmpty()) continue
            val pathIds = core.patternStopPathIds[patternId] ?: continue
            val lastId = pathIds.last()
            val prev = core.pendingPathUpdates[lastId]
            lastPathUpdates.add(
                StopPathAggregateUpdate(
                    stopPathId = lastId,
                    typicalTravelTimeSec = prev?.typicalTravelTimeSec,
                    typicalDwellTimeSec = prev?.typicalDwellTimeSec,
                    layoverStop = true,
                    breakTimeSec = median(gaps),
                ),
            )
        }
        writer.applyStopPathAggregates(lastPathUpdates)
    }

    private fun deriveCore(revisionId: Long): CoreResult {
        // Only stops with a resolvable coordinate: a stop with no (lat, lon) cannot be
        // projected or measured, so trips referencing one are skipped entirely (below).
        val stopCoord: Map<String, Point> =
            stops
                .findByRevisionId(revisionId)
                .mapNotNull {
                    val la = it.stopLat
                    val lo = it.stopLon
                    if (la != null && lo != null) it.stopId to Point(la, lo) else null
                }.toMap()
        val polylineCache = HashMap<String, Polyline?>()

        fun polyline(shapeId: String?): Polyline? {
            if (shapeId == null) return null
            return polylineCache.getOrPut(shapeId) {
                val pts =
                    shapePoints.findByShapeId(revisionId, shapeId).mapNotNull { p ->
                        val la = p.shapePtLat
                        val lo = p.shapePtLon
                        if (la != null && lo != null) Point(la, lo) else null
                    }
                if (pts.size >= 2) Polyline(pts) else null
            }
        }
        // Spec: "the trip's first gtfs_frequency row" — keep the first, not the last.
        val freqByTrip: Map<String, Frequency> = frequencies
            .findByRevisionId(revisionId)
            .groupBy {
                it.tripId
            }.mapValues { it.value.first() }

        val counts =
            mutableMapOf(
                "trip_patterns" to 0L,
                "stop_path" to 0L,
                "sched_trip" to 0L,
                "schedule_time" to 0L,
                "block" to 0L,
            )
        val derivedTrips = ArrayList<DerivedTrip>()
        val patternIdByKey = HashMap<String, Long>()
        val patternCumDist = HashMap<Long, DoubleArray>()
        val patternStopPathIds = HashMap<Long, List<Long>>()
        val routeExtents = HashMap<String, Extent>()
        val tripPatternLinks = HashMap<Long, Long>() // trips.id -> trip_patterns.id

        for (route in routes.findByRevisionId(revisionId)) {
            val routeTrips = trips.findByRouteId(revisionId, route.routeId)
            if (routeTrips.isEmpty()) continue
            // One round-trip per route (spec §5.3.1), not one per trip. The finder orders
            // by (tripId, stopSequence), so each grouped sublist is already sequenced.
            val stopTimesByTrip =
                stopTimes
                    .findByTripIds(
                        revisionId,
                        routeTrips.map { it.tripId }.distinct(),
                    ).groupBy { it.tripId }
            val pending =
                routeTrips
                    .map { t -> t to (stopTimesByTrip[t.tripId] ?: emptyList()) }
                    .filter { (_, rows) ->
                        rows.size >= 2 &&
                            rows.all { it.stopId != null }
                    }.filter { (trip, rows) -> hasResolvableCoords(trip, rows, stopCoord) }

            // Pass 1: new patterns for this route
            val newPatterns = ArrayList<TripPattern>()
            val newPatternPaths = LinkedHashMap<String, List<StopPath>>() // key -> unsaved paths
            for ((trip, rows) in pending) {
                withTripContext(trip) {
                    val stopIds = rows.map { it.stopId!! }
                    val key = PatternKey.of(trip.routeId, trip.shapeId, stopIds)
                    if (key in patternIdByKey || newPatterns.any { it.patternKey == key }) {
                        return@withTripContext
                    }
                    val build =
                        buildPattern(
                            revisionId,
                            trip,
                            rows,
                            stopIds,
                            stopCoord,
                            polyline(trip.shapeId),
                        )
                    newPatterns.add(build.pattern)
                    newPatternPaths[key] = build.paths
                }
            }
            writer.write(newPatterns) // assigns pattern ids
            counts["trip_patterns"] = counts["trip_patterns"]!! + newPatterns.size
            if (newPatterns.isNotEmpty()) {
                routeExtents[route.routeId] = Extent.ofExtents(newPatterns.map { it.extent })
            }
            val allPaths = ArrayList<StopPath>()
            for (p in newPatterns) {
                patternIdByKey[p.patternKey] = p.id!!
                val paths = newPatternPaths[p.patternKey]!!
                paths.forEach { it.tripPatternId = p.id!! }
                allPaths.addAll(paths)
            }
            writer.write(allPaths) // assigns stop_path ids
            counts["stop_path"] = counts["stop_path"]!! + allPaths.size
            for (p in newPatterns) {
                val paths = allPaths.filter { it.tripPatternId == p.id!! }.sortedBy { it.stopPathIndex }
                patternStopPathIds[p.id!!] = paths.map { it.id!! }
                patternCumDist[p.id!!] = cumulativeFromPaths(paths)
            }

            // Pass 2: sched_trips + schedule_times for this route
            val newTrips = ArrayList<SchedTrip>()
            val pendingTimes =
                ArrayList<
                    Pair<Int, List<ResolvedScheduleTime>>,
                >() // index into newTrips -> resolved
            for ((trip, rows) in pending) {
                withTripContext(trip) {
                    val stopIds = rows.map { it.stopId!! }
                    val patternId = patternIdByKey[PatternKey.of(trip.routeId, trip.shapeId, stopIds)]!!
                    val cum = patternCumDist[patternId]!!
                    val raw =
                        rows.mapIndexed { i, r ->
                            RawStopTime(i, r.arrivalTime, r.departureTime)
                        }
                    var resolved = ScheduleInterpolator.resolve(raw, cum)
                    val freq = freqByTrip[trip.tripId]
                    if (freq != null) {
                        val base = resolved.first().departureSec
                        resolved =
                            resolved.map {
                                it.copy(
                                    arrivalSec = it.arrivalSec - base,
                                    departureSec = it.departureSec - base,
                                )
                            }
                    }
                    val gtfsBlockId = trip.blockId?.ifBlank { null }
                    val st =
                        SchedTrip(
                            revisionId = revisionId,
                            tripPatternId = patternId,
                            tripId = trip.tripId,
                            routeId = trip.routeId,
                            serviceId = trip.serviceId,
                            directionId = trip.directionId,
                            headsign = trip.tripHeadsign,
                            tripShortName = trip.tripShortName,
                            startTimeSec = resolved.first().departureSec,
                            endTimeSec = resolved.last().arrivalSec,
                            frequencyBased = freq != null,
                            exactTimes = freq?.exactTimes,
                        )
                    newTrips.add(st)
                    pendingTimes.add(newTrips.lastIndex to resolved)
                    derivedTrips.add(
                        DerivedTrip(
                            schedTripId = -1,
                            patternId = patternId,
                            blockId = gtfsBlockId,
                            serviceId = st.serviceId,
                            routeId = st.routeId,
                            startSec = st.startTimeSec,
                            endSec = st.endTimeSec,
                            firstStopId = stopIds.first(),
                            lastStopId = stopIds.last(),
                            frequencyBased = st.frequencyBased,
                            resolved = resolved,
                        ),
                    )
                }
            }
            writer.write(newTrips) // assigns sched_trip ids
            counts["sched_trip"] = counts["sched_trip"]!! + newTrips.size
            // record each raw trip -> its derived pattern
            for ((trip, rows) in pending) {
                val stopIds = rows.map { it.stopId!! }
                patternIdByKey[PatternKey.of(trip.routeId, trip.shapeId, stopIds)]?.let {
                    tripPatternLinks[trip.id!!] = it
                }
            }
            // back-fill the schedTripId now known
            val baseDerivedIdx = derivedTrips.size - newTrips.size
            newTrips.forEachIndexed { i, st ->
                val dt = derivedTrips[baseDerivedIdx + i]
                derivedTrips[baseDerivedIdx + i] =
                    DerivedTrip(
                        st.id!!,
                        dt.patternId,
                        dt.blockId,
                        dt.serviceId,
                        dt.routeId,
                        dt.startSec,
                        dt.endSec,
                        dt.firstStopId,
                        dt.lastStopId,
                        dt.frequencyBased,
                        dt.resolved,
                    )
            }
            val times = ArrayList<ScheduleTime>()
            for ((tripIdx, resolved) in pendingTimes) {
                val st = newTrips[tripIdx]
                for (r in resolved) {
                    times.add(
                        ScheduleTime(
                            revisionId = revisionId,
                            schedTripId = st.id!!,
                            stopPathIndex = r.stopPathIndex,
                            arrivalSec = r.arrivalSec,
                            departureSec = r.departureSec,
                            interpolated = r.interpolated,
                            schedTravelTimeSec = r.schedTravelTimeSec,
                            schedDwellTimeSec = r.schedDwellTimeSec,
                        ),
                    )
                }
            }
            writer.write(times)
            counts["schedule_time"] = counts["schedule_time"]!! + times.size
        }

        derivedGtfsWriter.applyTripPatternLinks(tripPatternLinks)
        return CoreResult(counts, derivedTrips, patternStopPathIds, routeExtents)
    }

    /** Route extent = union of its patterns; agency extent = union of its routes. */
    private fun extentPass(
        revisionId: Long,
        core: CoreResult,
    ) {
        derivedGtfsWriter.applyRouteExtents(revisionId, core.routeExtents)
        val agencyExtents = HashMap<String, Extent>()
        for (route in routes.findByRevisionId(revisionId)) {
            val re = core.routeExtents[route.routeId] ?: continue
            val aid = route.agencyId ?: continue
            agencyExtents.getOrPut(aid) { Extent() }.add(re)
        }
        derivedGtfsWriter.applyAgencyExtents(revisionId, agencyExtents)
    }

    /**
     * True when every stop the trip visits has a resolvable `(lat, lon)`. A dangling or coordinate-less `stop_times.stop_id` would otherwise silently become `(0, 0)` and feed a ~5,700 km leg into `length_m`, the bbox and the interpolation weighting, so such a
     * trip is skipped: it simply produces no `sched_trip` row.
     */
    private fun hasResolvableCoords(
        trip: Trip,
        rows: List<StopTime>,
        stopCoord: Map<String, Point>,
    ): Boolean {
        val missing = rows.firstOrNull { it.stopId !in stopCoord } ?: return true
        log.warn(
            "skipping trip {} (route {}): stop {} has no resolvable coordinates",
            trip.tripId,
            trip.routeId,
            missing.stopId,
        )
        return false
    }

    /** Runs one trip's derivation, rethrowing any failure with the trip and route named — otherwise a single malformed trip fails the whole revision with a message ("first and last stop must have a time") that identifies nothing. */
    private inline fun <T> withTripContext(
        trip: Trip,
        body: () -> T,
    ): T =
        try {
            body()
        } catch (e: Exception) {
            throw IllegalStateException("trip ${trip.tripId} (route ${trip.routeId}): ${e.message}", e)
        }

    private class PatternBuild(
        val pattern: TripPattern,
        val paths: List<StopPath>,
    )

    private fun buildPattern(
        revisionId: Long,
        trip: Trip,
        rows: List<StopTime>,
        stopIds: List<String>,
        stopCoord: Map<String, Point>,
        line: Polyline?,
    ): PatternBuild {
        // Callers filter out trips with unresolvable stop coordinates, so this never misses.
        val coords = stopIds.map { stopCoord.getValue(it) }
        val projected = DoubleArray(stopIds.size) { Double.NaN }
        if (line != null) {
            var last = 0.0
            for (i in stopIds.indices) {
                val pr = line.project(coords[i])
                val d =
                    if (pr.deviationM > props.stopProjectionMaxDeviationM) {
                        Double.NaN
                    } else {
                        maxOf(pr.distanceAlong, last)
                    }
                projected[i] = d
                if (!d.isNaN()) last = d
            }
        }
        val paths = ArrayList<StopPath>(stopIds.size)
        val hasTimepointColumn = rows.any { it.timepoint != null }
        for (i in stopIds.indices) {
            val r = rows[i]
            val geom: List<Point>
            val segLen: Double
            when {
                i == 0 -> {
                    geom = listOf(coords[0])
                    segLen = 0.0
                }

                line != null && !projected[i - 1].isNaN() && !projected[i].isNaN() && projected[i] > projected[i - 1] -> {
                    geom = ShapeProjection.slice(line, projected[i - 1], projected[i])
                    segLen = projected[i] - projected[i - 1]
                }

                else -> {
                    geom = ShapeProjection.straightLine(coords[i - 1], coords[i])
                    segLen =
                        haversineMeters(
                            coords[i - 1].lat,
                            coords[i - 1].lon,
                            coords[i].lat,
                            coords[i].lon,
                        )
                }
            }
            val timed =
                if (hasTimepointColumn) {
                    r.timepoint == 1
                } else {
                    r.arrivalTime != null || r.departureTime != null
                }
            paths.add(
                StopPath(
                    revisionId = revisionId,
                    tripPatternId = 0L,
                    stopPathIndex = i,
                    stopId = stopIds[i],
                    routeId = trip.routeId,
                    gtfsStopSeq = r.stopSequence,
                    lengthM = segLen,
                    pathGeometry = json.writeValueAsString(geom.map { listOf(it.lon, it.lat) }),
                    pickupType = r.pickupType,
                    dropOffType = r.dropOffType,
                    waitStop = timed,
                    scheduleAdherenceStop = timed,
                    layoverStop = i == 0,
                    breakTimeSec = null,
                    typicalTravelTimeSec = null,
                    typicalDwellTimeSec = null,
                ),
            )
        }
        val pattern =
            TripPattern(
                revisionId = revisionId,
                patternKey = PatternKey.of(trip.routeId, trip.shapeId, stopIds),
                routeId = trip.routeId,
                routeShortName = trip.routeId,
                directionId = trip.directionId,
                headsign = trip.tripHeadsign,
                shapeId = trip.shapeId,
                stopCount = stopIds.size,
                lengthM = paths.sumOf { it.lengthM },
                extent = Extent.of(coords),
                tripCount = 0,
            )
        return PatternBuild(pattern, paths)
    }

    private fun cumulativeFromPaths(paths: List<StopPath>): DoubleArray {
        val out = DoubleArray(paths.size)
        for (i in 1 until paths.size) out[i] = out[i - 1] + paths[i].lengthM
        return out
    }

    override fun postProcess(revisionId: Long): Any {
        writer.deleteForRevision(revisionId)
        try {
            val core = deriveCore(revisionId)
            aggregatePass(core)
            blockPass(revisionId, core)
            extentPass(revisionId, core)
            return core.counts
        } catch (e: Throwable) {
            // Throwable, not Exception: an Error mid-derive would otherwise strand the
            // revision in DERIVING, which permanently blocks that feed's future ingests.
            log.warn("schedule derivation failed for revision {}: {}", revisionId, e.message)
            runCatching { writer.deleteForRevision(revisionId) }
            throw e
        }
    }

    override fun onIngestionFailure(revisionId: Long) {
        runCatching { writer.deleteForRevision(revisionId) }
    }

    @EventListener
    fun onIngestionFailed(ingestionFailedEvent: IngestionFailedEvent) {
        log.warn("ingestion failed: {}", ingestionFailedEvent)
        runCatching { writer.deleteForRevision(ingestionFailedEvent.revisionId) }
    }
}
