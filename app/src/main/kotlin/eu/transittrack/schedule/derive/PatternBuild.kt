package eu.transittrack.schedule.derive

import org.slf4j.Logger

import eu.transittrack.geo.BBox
import eu.transittrack.geo.Line
import eu.transittrack.geo.Point
import eu.transittrack.geo.Position
import eu.transittrack.geo.algorithm.haversineDistance
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
 * produces no derived schedule (no `trip_pattern_id`, no `schedule_time` rows).
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
    line: Line?,
    maxDeviationM: Double,
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
    // Pattern polyline = every segment concatenated; adjacent segments share their boundary point.
    val patternPoints = ArrayList<Position>()
    val hasTimepointColumn = rows.any { it.timepoint != null }
    for (i in stopIds.indices) {
        val r = rows[i]
        val segment: List<Position>
        val segLen: Double
        when {
            i == 0 -> {
                segment = listOf(coords[0].coordinates)
                segLen = 0.0
            }

            line != null && !projected[i - 1].isNaN() && !projected[i].isNaN() && projected[i] > projected[i - 1] -> {
                segment = line.slice(projected[i - 1], projected[i]).coordinates
                segLen = projected[i] - projected[i - 1]
            }

            else -> {
                segment = listOf(coords[i - 1].coordinates, coords[i].coordinates)
                segLen = haversineDistance(coords[i - 1].coordinates, coords[i].coordinates)
            }
        }
        for (p in segment) {
            if (patternPoints.isEmpty() || patternPoints.last() != p) patternPoints.add(p)
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
                stopSeq = r.stopSequence,
                lengthM = segLen,
                pickupType = r.pickupType,
                dropOffType = r.dropOffType,
                waitStop = timed,
                scheduleAdherenceStop = timed,
                layoverStop = i == 0,
                breakTimeSec = null,
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
            extent = BBox.of(coords),
            tripCount = 0,
            geometry = if (patternPoints.size >= 2) Line(patternPoints) else null,
        )
    return PatternBuild(pattern, paths)
}
