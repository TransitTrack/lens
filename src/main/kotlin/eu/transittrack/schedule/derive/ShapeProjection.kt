package eu.transittrack.schedule.derive

import eu.transittrack.Point
import eu.transittrack.Polyline
import kotlin.math.max
import kotlin.math.min

object ShapeProjection {
    fun straightLine(a: Point, b: Point): List<Point> = listOf(a, b)

    fun slice(line: Polyline, fromM: Double, toM: Double): List<Point> {
        val lo = max(0.0, min(fromM, toM))
        val hi = min(line.lengthM, max(fromM, toM))
        val out = ArrayList<Point>()
        out.add(line.pointAt(lo))
        for (i in line.points.indices) {
            if (line.cumulative[i] > lo && line.cumulative[i] < hi) out.add(line.points[i])
        }
        out.add(line.pointAt(hi))
        return out
    }
}
