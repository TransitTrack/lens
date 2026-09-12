package eu.transittrack.avl.match

import org.springframework.stereotype.Component

import eu.transittrack.Point
import eu.transittrack.Polyline
import eu.transittrack.Vector
import eu.transittrack.avl.AvlProperties

/** A trip pattern's shape as one polyline, plus where each stop path begins along it (metres). */
@Suppress("ArrayInDataClass")
data class PatternGeometry(
    val line: Polyline,
    val stopPathCumM: DoubleArray,
    /** Parallel to [stopPathCumM]: whether that stop path ends in a scheduled layover. */
    val layoverStop: BooleanArray,
) {
    /** End-of-path location for stop path [i] — where it ends along [line]. */
    fun endOfPath(i: Int): Point = line.pointAt(if (i + 1 < stopPathCumM.size) stopPathCumM[i + 1] else line.lengthM)
}

data class SpatialMatch(
    val stopPathIndex: Int,
    val distanceAlongTripM: Double,
    val deviationM: Double,
    val snapped: Point,
    val heading: Double?,
)

/**
 * Projects a raw position onto a trip pattern's shape. Returns `null` when the position is farther
 * than `match.maxDeviationM` from the shape (and no allowable layover stop nearby — see below), or
 * when it lands more than `match.backtrackToleranceM` behind [minAlongM] (the vehicle's previous
 * along-trip distance on this same trip).
 *
 * Layover stop paths (`stop_path.layover_stop`) are exempt from the shape-deviation check — a
 * vehicle is allowed to be off-route during a scheduled layover, e.g. parked at a depot — mirroring
 * transitclock's `SpatialMatcher.withinAllowableDistanceOfLayover`: when the plain shape projection
 * doesn't land close enough to the shape, fall back to the nearest layover stop the vehicle is
 * within an allowable distance of.
 */
@Component
class SpatialMatcher(
    props: AvlProperties,
) {
    private val cfg = props.match

    fun match(
        geom: PatternGeometry,
        report: Point,
        minAlongM: Double?,
    ): SpatialMatch? {
        val line = geom.line
        if (line.points.size < 2) return null
        val pr = line.project(report)
        if (minAlongM != null && pr.distanceAlong < minAlongM - cfg.backtrackToleranceM) return null

        if (pr.deviationM > cfg.maxDeviationM) {
            return nearestAllowableLayover(geom, report, minAlongM)
        }

        val idx = stopPathIndexAt(geom.stopPathCumM, pr.distanceAlong)
        val heading =
            if (line.lengthM < 10.0) {
                null
            } else {
                val a = (pr.distanceAlong - 5.0).coerceIn(0.0, line.lengthM)
                val b = (pr.distanceAlong + 5.0).coerceIn(0.0, line.lengthM)
                Vector(line.pointAt(a), line.pointAt(b)).heading()
            }
        return SpatialMatch(idx, pr.distanceAlong, pr.deviationM, line.pointAt(pr.distanceAlong), heading)
    }

    /**
     * Closest layover stop path the report is within its allowable distance of, or `null` if none.
     * The first stop path of a pattern is always allowed (deadheading in, per the reference). For
     * any other layover, allowable = `max(1.5x the distance from the previous stop, layoverDistanceM)`.
     * Heading is irrelevant at a layover — the vehicle is free to roam — so it's left `null`.
     */
    private fun nearestAllowableLayover(
        geom: PatternGeometry,
        report: Point,
        minAlongM: Double?,
    ): SpatialMatch? {
        var best: SpatialMatch? = null
        var bestDistanceM = Double.MAX_VALUE
        for (i in geom.layoverStop.indices) {
            if (!geom.layoverStop[i]) continue
            val along = if (i + 1 < geom.stopPathCumM.size) geom.stopPathCumM[i + 1] else geom.line.lengthM
            if (minAlongM != null && along < minAlongM - cfg.backtrackToleranceM) continue

            val layoverLoc = geom.endOfPath(i)
            val distanceToLayoverM = report.distance(layoverLoc)
            val allowableM =
                if (i == 0) {
                    Double.MAX_VALUE
                } else {
                    val distanceBtwnStopsM = layoverLoc.distance(geom.endOfPath(i - 1))
                    maxOf(distanceBtwnStopsM * 1.5, cfg.layoverDistanceM)
                }
            if (distanceToLayoverM < allowableM && distanceToLayoverM < bestDistanceM) {
                bestDistanceM = distanceToLayoverM
                best = SpatialMatch(i, along, distanceToLayoverM, layoverLoc, heading = null)
            }
        }
        return best
    }

    /** Last index whose cumulative start is <= [along]; clamped to `[0, size-1]`. */
    private fun stopPathIndexAt(
        cum: DoubleArray,
        along: Double,
    ): Int {
        var lo = 0
        var hi = cum.size - 1
        var ans = 0
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (cum[mid] <= along) {
                ans = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return ans
    }
}
