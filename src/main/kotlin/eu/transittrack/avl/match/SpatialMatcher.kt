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
)

data class SpatialMatch(
    val stopPathIndex: Int,
    val distanceAlongTripM: Double,
    val deviationM: Double,
    val snapped: Point,
    val heading: Double?,
)

/**
 * Projects a raw position onto a trip pattern's shape. Returns `null` when the position is farther
 * than `match.maxDeviationM` from the shape, or when it lands more than `match.backtrackToleranceM`
 * behind [minAlongM] (the vehicle's previous along-trip distance on this same trip).
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
        if (pr.deviationM > cfg.maxDeviationM) return null
        if (minAlongM != null && pr.distanceAlong < minAlongM - cfg.backtrackToleranceM) return null

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
