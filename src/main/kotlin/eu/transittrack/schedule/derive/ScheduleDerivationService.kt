package eu.transittrack.schedule.derive

import eu.transittrack.haversineMeters
import eu.transittrack.gtfs.model.GtfsFrequency
import eu.transittrack.gtfs.model.GtfsFrequencyRepository
import eu.transittrack.gtfs.model.GtfsRouteRepository
import eu.transittrack.gtfs.model.GtfsShapePointRepository
import eu.transittrack.gtfs.model.GtfsStopRepository
import eu.transittrack.gtfs.model.GtfsStopTime
import eu.transittrack.gtfs.model.GtfsStopTimeRepository
import eu.transittrack.gtfs.model.GtfsTrip
import eu.transittrack.gtfs.model.GtfsTripRepository
import eu.transittrack.schedule.config.ScheduleProperties
import eu.transittrack.schedule.model.SchedTrip
import eu.transittrack.schedule.model.ScheduleTime
import eu.transittrack.schedule.model.StopPath
import eu.transittrack.schedule.model.TripPattern
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper

/**
 * Derives the schedule model for one GTFS revision: trip patterns + stop paths
 * (this task), plus schedule times per trip. Pattern/block aggregate passes are
 * added in the next task. Owns its failure cleanup: any exception wipes the
 * revision's derived rows and rethrows.
 */
@Service
class ScheduleDerivationService(
    private val stops: GtfsStopRepository,
    private val routes: GtfsRouteRepository,
    private val trips: GtfsTripRepository,
    private val stopTimes: GtfsStopTimeRepository,
    private val shapePoints: GtfsShapePointRepository,
    private val frequencies: GtfsFrequencyRepository,
    private val writer: ScheduleWriter,
    private val props: ScheduleProperties,
    private val json: JsonMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** Holds what later passes (Task 10) need without reloading entities. */
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
        val resolved: List<ResolvedScheduleTime>,
    )

    /** Result carried out of [deriveCore] for reuse by the aggregate/block passes. */
    class CoreResult(
        val counts: MutableMap<String, Long>,
        val derivedTrips: List<DerivedTrip>,
        val patternCumDist: Map<Long, DoubleArray>,
        val patternStopPathIds: Map<Long, List<Long>>,
    )

    fun derive(revisionId: Long): Map<String, Long> {
        writer.deleteForRevision(revisionId)
        return try {
            deriveCore(revisionId).counts
        } catch (e: Exception) {
            log.warn("schedule derivation failed for revision {}: {}", revisionId, e.message)
            runCatching { writer.deleteForRevision(revisionId) }
            throw e
        }
    }

    private fun deriveCore(revisionId: Long): CoreResult {
        val stopCoord: Map<String, Point> = stops.findByRevisionId(revisionId).associate {
            it.stopId to Point(it.stopLat ?: 0.0, it.stopLon ?: 0.0)
        }
        val polylineCache = HashMap<String, Polyline?>()
        fun polyline(shapeId: String?): Polyline? {
            if (shapeId == null) return null
            return polylineCache.getOrPut(shapeId) {
                val pts = shapePoints.findByRevisionIdAndShapeIdOrderByShapePtSequence(revisionId, shapeId)
                    .mapNotNull { p ->
                        val la = p.shapePtLat; val lo = p.shapePtLon
                        if (la != null && lo != null) Point(la, lo) else null
                    }
                if (pts.size >= 2) Polyline(pts) else null
            }
        }
        val freqByTrip: Map<String, GtfsFrequency> =
            frequencies.findByRevisionId(revisionId).associateBy { it.tripId }

        val counts = mutableMapOf(
            "trip_pattern" to 0L, "stop_path" to 0L, "sched_trip" to 0L,
            "schedule_time" to 0L, "block" to 0L,
        )
        val derivedTrips = ArrayList<DerivedTrip>()
        val patternIdByKey = HashMap<String, Long>()
        val patternCumDist = HashMap<Long, DoubleArray>()
        val patternStopPathIds = HashMap<Long, List<Long>>()

        for (route in routes.findByRevisionId(revisionId)) {
            val routeTrips = trips.findByRevisionIdAndRouteId(revisionId, route.routeId)
            val pending = routeTrips.map { t ->
                t to stopTimes.findByRevisionIdAndTripIdOrderByStopSequence(revisionId, t.tripId)
            }.filter { (_, rows) -> rows.size >= 2 && rows.all { it.stopId != null } }

            // Pass 1: new patterns for this route
            val newPatterns = ArrayList<TripPattern>()
            val newPatternPaths = LinkedHashMap<String, List<StopPath>>()   // key -> unsaved paths
            for ((trip, rows) in pending) {
                val stopIds = rows.map { it.stopId!! }
                val key = PatternKey.of(trip.shapeId, stopIds)
                if (key in patternIdByKey || newPatterns.any { it.patternKey == key }) continue
                val build = buildPattern(revisionId, trip, rows, stopIds, stopCoord, polyline(trip.shapeId))
                newPatterns.add(build.pattern)
                newPatternPaths[key] = build.paths
            }
            writer.write(newPatterns)                       // assigns pattern ids
            counts["trip_pattern"] = counts["trip_pattern"]!! + newPatterns.size
            val allPaths = ArrayList<StopPath>()
            for (p in newPatterns) {
                patternIdByKey[p.patternKey] = p.id!!
                val paths = newPatternPaths[p.patternKey]!!
                paths.forEach { it.tripPatternId = p.id!! }
                allPaths.addAll(paths)
            }
            writer.write(allPaths)                          // assigns stop_path ids
            counts["stop_path"] = counts["stop_path"]!! + allPaths.size
            for (p in newPatterns) {
                val paths = allPaths.filter { it.tripPatternId == p.id!! }.sortedBy { it.stopPathIndex }
                patternStopPathIds[p.id!!] = paths.map { it.id!! }
                patternCumDist[p.id!!] = cumulativeFromPaths(paths)
            }

            // Pass 2: sched_trips + schedule_times for this route
            val newTrips = ArrayList<SchedTrip>()
            val pendingTimes = ArrayList<Pair<Int, List<ResolvedScheduleTime>>>() // index into newTrips -> resolved
            for ((trip, rows) in pending) {
                val stopIds = rows.map { it.stopId!! }
                val patternId = patternIdByKey[PatternKey.of(trip.shapeId, stopIds)]!!
                val cum = patternCumDist[patternId]!!
                val raw = rows.mapIndexed { i, r -> RawStopTime(i, r.arrivalTime, r.departureTime) }
                var resolved = ScheduleInterpolator.resolve(raw, cum)
                val freq = freqByTrip[trip.tripId]
                if (freq != null) {
                    val base = resolved.first().departureSec
                    resolved = resolved.map {
                        it.copy(arrivalSec = it.arrivalSec - base, departureSec = it.departureSec - base)
                    }
                }
                val st = SchedTrip(
                    revisionId = revisionId, tripPatternId = patternId, tripId = trip.tripId,
                    routeId = trip.routeId, serviceId = trip.serviceId, directionId = trip.directionId,
                    headsign = trip.tripHeadsign, tripShortName = trip.tripShortName,
                    blockId = trip.blockId?.ifBlank { null }, blockSeq = null,
                    layoverAfterSec = null, deadheadAfter = null,
                    startTimeSec = resolved.first().departureSec, endTimeSec = resolved.last().arrivalSec,
                    frequencyBased = freq != null, exactTimes = freq?.exactTimes,
                )
                newTrips.add(st)
                pendingTimes.add(newTrips.lastIndex to resolved)
                derivedTrips.add(
                    DerivedTrip(
                        schedTripId = -1, patternId = patternId, blockId = st.blockId,
                        serviceId = st.serviceId, routeId = st.routeId,
                        startSec = st.startTimeSec, endSec = st.endTimeSec,
                        firstStopId = stopIds.first(), lastStopId = stopIds.last(),
                        resolved = resolved,
                    ),
                )
            }
            writer.write(newTrips)                          // assigns sched_trip ids
            counts["sched_trip"] = counts["sched_trip"]!! + newTrips.size
            // back-fill the schedTripId now known
            val baseDerivedIdx = derivedTrips.size - newTrips.size
            newTrips.forEachIndexed { i, st ->
                val dt = derivedTrips[baseDerivedIdx + i]
                derivedTrips[baseDerivedIdx + i] = DerivedTrip(
                    st.id!!, dt.patternId, dt.blockId, dt.serviceId, dt.routeId,
                    dt.startSec, dt.endSec, dt.firstStopId, dt.lastStopId, dt.resolved,
                )
            }
            val times = ArrayList<ScheduleTime>()
            for ((tripIdx, resolved) in pendingTimes) {
                val st = newTrips[tripIdx]
                for (r in resolved) {
                    times.add(
                        ScheduleTime(
                            revisionId = revisionId, schedTripId = st.id!!, stopPathIndex = r.stopPathIndex,
                            arrivalSec = r.arrivalSec, departureSec = r.departureSec,
                            interpolated = r.interpolated,
                            schedTravelTimeSec = r.schedTravelTimeSec, schedDwellTimeSec = r.schedDwellTimeSec,
                        ),
                    )
                }
            }
            writer.write(times)
            counts["schedule_time"] = counts["schedule_time"]!! + times.size
        }

        return CoreResult(counts, derivedTrips, patternCumDist, patternStopPathIds)
    }

    private class PatternBuild(val pattern: TripPattern, val paths: List<StopPath>)

    private fun buildPattern(
        revisionId: Long,
        trip: GtfsTrip,
        rows: List<GtfsStopTime>,
        stopIds: List<String>,
        stopCoord: Map<String, Point>,
        line: Polyline?,
    ): PatternBuild {
        val coords = stopIds.map { stopCoord[it] ?: Point(0.0, 0.0) }
        val projected = DoubleArray(stopIds.size) { Double.NaN }
        if (line != null) {
            var last = 0.0
            for (i in stopIds.indices) {
                val pr = line.project(coords[i])
                val d = if (pr.deviationM > props.stopProjectionMaxDeviationM) Double.NaN
                else maxOf(pr.distanceAlong, last)
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
                i == 0 -> { geom = listOf(coords[0]); segLen = 0.0 }
                line != null && !projected[i - 1].isNaN() && !projected[i].isNaN() &&
                    projected[i] > projected[i - 1] -> {
                    geom = ShapeProjection.slice(line, projected[i - 1], projected[i])
                    segLen = projected[i] - projected[i - 1]
                }
                else -> {
                    geom = ShapeProjection.straightLine(coords[i - 1], coords[i])
                    segLen = haversineMeters(coords[i - 1].lat, coords[i - 1].lon, coords[i].lat, coords[i].lon)
                }
            }
            val timed = if (hasTimepointColumn) r.timepoint == 1
            else r.arrivalTime != null || r.departureTime != null
            paths.add(
                StopPath(
                    revisionId = revisionId, tripPatternId = 0L, stopPathIndex = i, stopId = stopIds[i],
                    gtfsStopSeq = r.stopSequence, lengthM = segLen,
                    pathGeometry = json.writeValueAsString(geom.map { listOf(it.lon, it.lat) }),
                    pickupType = r.pickupType, dropOffType = r.dropOffType,
                    waitStop = timed, scheduleAdherenceStop = timed,
                    layoverStop = i == 0, breakTimeSec = null,
                    typicalTravelTimeSec = null, typicalDwellTimeSec = null,
                ),
            )
        }
        val lats = coords.map { it.lat }; val lons = coords.map { it.lon }
        val pattern = TripPattern(
            revisionId = revisionId,
            patternKey = PatternKey.of(trip.shapeId, stopIds),
            routeId = trip.routeId, directionId = trip.directionId, headsign = trip.tripHeadsign,
            shapeId = trip.shapeId, stopCount = stopIds.size,
            lengthM = paths.sumOf { it.lengthM },
            minLat = lats.min(), minLon = lons.min(), maxLat = lats.max(), maxLon = lons.max(),
            tripCount = 0,
        )
        return PatternBuild(pattern, paths)
    }

    private fun cumulativeFromPaths(paths: List<StopPath>): DoubleArray {
        val out = DoubleArray(paths.size)
        for (i in 1 until paths.size) out[i] = out[i - 1] + paths[i].lengthM
        return out
    }
}
