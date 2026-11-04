package eu.transittrack.geo

import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** A WGS-84 polyline with precomputed cumulative distances (metres) at each vertex. */
class Polyline(
    val points: List<Point>,
) {
    val cumulative: DoubleArray =
        DoubleArray(points.size).also { c ->
            for (i in 1 until points.size) {
                c[i] = c[i - 1] + haversineDistance(points[i - 1], points[i])
            }
        }

    val size: Int = points.size

    val lengthM: Double
        get() = if (cumulative.isEmpty()) 0.0 else cumulative.last()

    fun project(p: Point): Projection {
        if (points.size == 1) {
            return Projection(0.0, haversineDistance(p, points[0]))
        }

        var best = Projection(0.0, Double.MAX_VALUE)
        val cosLat = cos(Math.toRadians(p.lat))

        fun x(pt: Point) = Math.toRadians(pt.lon - p.lon) * cosLat * earthRadius

        fun y(pt: Point) = Math.toRadians(pt.lat - p.lat) * earthRadius

        for (i in 1 until points.size) {
            val ax = x(points[i - 1])
            val ay = y(points[i - 1])
            val bx = x(points[i])
            val by = y(points[i])
            val dx = bx - ax
            val dy = by - ay
            val segLen2 = dx * dx + dy * dy
            val t = if (segLen2 == 0.0) 0.0 else max(0.0, min(1.0, -(ax * dx + ay * dy) / segLen2))
            val projX = ax + t * dx
            val projY = ay + t * dy
            val dev = sqrt(projX * projX + projY * projY)
            if (dev < best.deviationM) {
                val along = cumulative[i - 1] + t * (cumulative[i] - cumulative[i - 1])
                best = Projection(along, dev)
            }
        }
        return best
    }

    fun pointAt(distanceAlong: Double): Point {
        if (points.isEmpty()) throw IllegalStateException("empty polyline")
        if (points.size == 1 || distanceAlong <= 0.0) return points.first()
        if (distanceAlong >= lengthM) return points.last()
        var i = 1
        while (i < points.size && cumulative[i] < distanceAlong) i++
        val segFrom = cumulative[i - 1]
        val segTo = cumulative[i]
        val t = if (segTo == segFrom) 0.0 else (distanceAlong - segFrom) / (segTo - segFrom)
        val a = points[i - 1]
        val b = points[i]
        return Point(a.lat + t * (b.lat - a.lat), a.lon + t * (b.lon - a.lon))
    }

    fun slice(fromM: Double, toM: Double): Polyline {
        val lo = max(0.0, min(fromM, toM))
        val hi = min(lengthM, max(fromM, toM))
        val out = ArrayList<Point>()
        out.add(pointAt(lo))
        for (i in points.indices) {
            if (cumulative[i] > lo && cumulative[i] < hi) out.add(points[i])
        }
        out.add(pointAt(hi))
        return Polyline(out)
    }


    companion object {
        fun of(points: List<Point>): Polyline {
            return Polyline(points)
        }

        fun of(vararg points: Point): Polyline {
            return Polyline(points.toList())
        }
    }
}
