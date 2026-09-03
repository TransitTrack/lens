package eu.transittrack.schedule.derive

import org.slf4j.Logger
import tools.jackson.databind.json.JsonMapper

import eu.transittrack.Extent
import eu.transittrack.Point
import eu.transittrack.Polyline
import eu.transittrack.haversineMeters
import eu.transittrack.schedule.model.StopPath
import eu.transittrack.schedule.model.TripPattern

class PatternBuild(
    val pattern: TripPattern,
    val paths: List<StopPath>,
)

fun cumulativeFromPaths(paths: List<StopPath>): DoubleArray {
    val out = DoubleArray(paths.size)
    for (i in 1 until paths.size) out[i] = out[i - 1] + paths[i].lengthM
    return out
}

/**
 * True when every stop the trip visits has a resolvable `(lat, lon)`. A dangling or coordinate-less
 * `stop_times.stop_id` would otherwise silently become `(0, 0)` and feed a ~5,700 km leg into
 * `length_m`, the bbox and the interpolation weighting, so such a trip is skipped: it simply
 * produces no `sched_trip` row.
 */
fun hasResolvableCoords(
    tripId: String,
    routeId: String,
    stopIds: List<String>,
    stopCoord: Map<String, Point>,
    log: Logger,
): Boolean {
    val missing = stopIds.firstOrNull { it !in stopCoord } ?: return true
    log.warn(
        "skipping trip {} (route {}): stop {} has no resolvable coordinates",
        tripId,
        routeId,
        missing,
    )
    return false
}

fun buildPattern(
    revisionId: Long,
    routeId: String,
    shapeId: String?,
    directionId: Int?,
    headsign: String?,
    rows: List<CleanStopTime>,
    stopIds: List<String>,
    stopCoord: Map<String, Point>,
    line: Polyline?,
    maxDeviationM: Double,
    json: JsonMapper,
): PatternBuild {
    // Callers filter out trips with unresolvable stop coordinates, so this never misses.
    val coords = stopIds.map { stopCoord.getValue(it) }
    val projected = DoubleArray(stopIds.size) { Double.NaN }
    if (line != null) {
        var last = 0.0
        for (i in stopIds.indices) {
            val pr = line.project(coords[i])
            val d =
                if (pr.deviationM > maxDeviationM) {
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
                r.arrivalSec != null || r.departureSec != null
            }
        paths.add(
            StopPath(
                revisionId = revisionId,
                tripPatternId = 0L,
                stopPathIndex = i,
                stopId = stopIds[i],
                routeId = routeId,
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
            patternKey = PatternKey.of(routeId, shapeId, stopIds),
            routeId = routeId,
            routeShortName = routeId,
            directionId = directionId,
            headsign = headsign,
            shapeId = shapeId,
            stopCount = stopIds.size,
            lengthM = paths.sumOf { it.lengthM },
            extent = Extent.of(coords),
            tripCount = 0,
        )
    return PatternBuild(pattern, paths)
}
