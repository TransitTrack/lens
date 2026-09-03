package eu.transittrack.schedule.derive

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper

import eu.transittrack.Extent
import eu.transittrack.Point
import eu.transittrack.Polyline
import eu.transittrack.gtfs.ingest.IngestionPostProcessor
import eu.transittrack.gtfs.model.RouteRepository
import eu.transittrack.gtfs.model.ShapePointRepository
import eu.transittrack.gtfs.model.StopRepository
import eu.transittrack.gtfs.model.StopTimeRepository
import eu.transittrack.gtfs.model.Trip
import eu.transittrack.gtfs.model.TripRepository
import eu.transittrack.schedule.ScheduleProperties
import eu.transittrack.schedule.model.StopPath
import eu.transittrack.schedule.model.TripPattern

/**
 * Stage 1 of schedule derivation. Per route: clean each trip's stop_times (consecutive-stop dedup /
 * wait-stop reconstruction), then build `trip_patterns` + `stop_path` keyed route|shape|stops.
 * Opens the shared [DerivationContext] and pre-cleans the revision's derived schedule tables.
 */
@Component
@Order(TripPatternProcessor.ORDER)
@ConditionalOnProperty(name = ["transittrack.schedule.enabled"], havingValue = "true")
class TripPatternProcessor(
    private val context: DerivationContext,
    private val writer: ScheduleWriter,
    private val props: ScheduleProperties,
    private val json: JsonMapper,
    private val routes: RouteRepository,
    private val trips: TripRepository,
    private val stopTimes: StopTimeRepository,
    private val stops: StopRepository,
    private val shapePoints: ShapePointRepository,
) : IngestionPostProcessor {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        const val ORDER = 10
    }

    override fun postProcess(revisionId: Long): Map<String, Long> {
        writer.deleteForRevision(revisionId) // idempotent pre-clean
        val state = context.open(revisionId)

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

        var patternCount = 0L
        var pathCount = 0L

        for (route in routes.findByRevisionId(revisionId)) {
            val routeTrips = trips.findByRouteId(revisionId, route.routeId)
            if (routeTrips.isEmpty()) continue
            val stByTrip =
                stopTimes
                    .findByTripIds(revisionId, routeTrips.map { it.tripId }.distinct())
                    .groupBy { it.tripId }

            // clean + filter
            data class Pending(
                val trip: Trip,
                val rows: List<CleanStopTime>,
                val stopIds: List<String>,
            )
            val pending = ArrayList<Pending>()
            for (t in routeTrips) {
                val raw = stByTrip[t.tripId] ?: emptyList()
                if (raw.any { it.stopId == null }) continue
                val cleaned = StopTimeCleaner.clean(raw)
                state.cleanedRows[t.tripId] = cleaned
                if (cleaned.size < 2) continue
                val stopIds = cleaned.map { it.stopId }
                if (!hasResolvableCoords(t.tripId, t.routeId, stopIds, stopCoord, log)) continue
                pending.add(Pending(t, cleaned, stopIds))
            }

            val newPatterns = ArrayList<TripPattern>()
            val newPaths = LinkedHashMap<String, List<StopPath>>()
            for (p in pending) {
                val key = PatternKey.of(p.trip.routeId, p.trip.shapeId, p.stopIds)
                if (key in state.patternIdByKey || newPatterns.any { it.patternKey == key }) continue
                val headsign = Headsigns.resolve(p.trip.tripHeadsign, p.rows.first().stopHeadsign)
                val build =
                    buildPattern(
                        revisionId, p.trip.routeId, p.trip.shapeId, p.trip.directionId, headsign,
                        p.rows, p.stopIds, stopCoord, polyline(p.trip.shapeId),
                        props.stopProjectionMaxDeviationM, json,
                    )
                newPatterns.add(build.pattern)
                newPaths[key] = build.paths
            }
            writer.write(newPatterns)
            patternCount += newPatterns.size
            if (newPatterns.isNotEmpty()) {
                state.patternExtents[route.routeId] = Extent.ofExtents(newPatterns.map { it.extent })
            }
            val allPaths = ArrayList<StopPath>()
            for (pat in newPatterns) {
                state.patternIdByKey[pat.patternKey] = pat.id!!
                val paths = newPaths.getValue(pat.patternKey)
                paths.forEach { it.tripPatternId = pat.id!! }
                allPaths.addAll(paths)
            }
            writer.write(allPaths)
            pathCount += allPaths.size
            for (pat in newPatterns) {
                val paths = allPaths.filter { it.tripPatternId == pat.id!! }.sortedBy { it.stopPathIndex }
                state.patternStopPathIds[pat.id!!] = paths.map { it.id!! }
                state.patternCumDist[pat.id!!] = cumulativeFromPaths(paths)
            }
        }

        return mapOf("trip_patterns" to patternCount, "stop_path" to pathCount)
    }

    override fun onIngestionFailure(revisionId: Long) {
        runCatching { writer.deleteTables(revisionId, "stop_path", "trip_patterns") }
        runCatching { context.close(revisionId) }
    }
}
