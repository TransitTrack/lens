package eu.transittrack.schedule.derive

import eu.transittrack.haversineMeters
import eu.transittrack.toRadians
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class LatLon(val lat: Double, val lon: Double)

data class Projection(val distanceAlong: Double, val deviationM: Double)

/** A WGS-84 polyline with precomputed cumulative distances (metres) at each vertex. */
class Polyline(val points: List<LatLon>) {
    val cumulative: DoubleArray = DoubleArray(points.size).also { c ->
        for (i in 1 until points.size) {
            c[i] = c[i - 1] + haversineMeters(
                points[i - 1].lat, points[i - 1].lon, points[i].lat, points[i].lon,
            )
        }
    }
    val lengthM: Double get() = if (cumulative.isEmpty()) 0.0 else cumulative.last()

    private val earthR = 6_371_000.0

    fun project(p: LatLon): Projection {
        if (points.size == 1) {
            return Projection(0.0, haversineMeters(p.lat, p.lon, points[0].lat, points[0].lon))
        }
        var best = Projection(0.0, Double.MAX_VALUE)
        val cosLat = cos(toRadians(p.lat))
        fun x(pt: LatLon) = toRadians(pt.lon - p.lon) * cosLat * earthR
        fun y(pt: LatLon) = toRadians(pt.lat - p.lat) * earthR
        for (i in 1 until points.size) {
            val ax = x(points[i - 1]); val ay = y(points[i - 1])
            val bx = x(points[i]); val by = y(points[i])
            val dx = bx - ax; val dy = by - ay
            val segLen2 = dx * dx + dy * dy
            val t = if (segLen2 == 0.0) 0.0 else max(0.0, min(1.0, -(ax * dx + ay * dy) / segLen2))
            val projX = ax + t * dx; val projY = ay + t * dy
            val dev = sqrt(projX * projX + projY * projY)
            if (dev < best.deviationM) {
                val along = cumulative[i - 1] + t * (cumulative[i] - cumulative[i - 1])
                best = Projection(along, dev)
            }
        }
        return best
    }

    fun pointAt(distanceAlong: Double): LatLon {
        if (points.isEmpty()) throw IllegalStateException("empty polyline")
        if (points.size == 1 || distanceAlong <= 0.0) return points.first()
        if (distanceAlong >= lengthM) return points.last()
        var i = 1
        while (i < points.size && cumulative[i] < distanceAlong) i++
        val segFrom = cumulative[i - 1]; val segTo = cumulative[i]
        val t = if (segTo == segFrom) 0.0 else (distanceAlong - segFrom) / (segTo - segFrom)
        val a = points[i - 1]; val b = points[i]
        return LatLon(a.lat + t * (b.lat - a.lat), a.lon + t * (b.lon - a.lon))
    }
}

object ShapeProjection {
    fun straightLine(a: LatLon, b: LatLon): List<LatLon> = listOf(a, b)

    fun slice(line: Polyline, fromM: Double, toM: Double): List<LatLon> {
        val lo = max(0.0, min(fromM, toM))
        val hi = min(line.lengthM, max(fromM, toM))
        val out = ArrayList<LatLon>()
        out.add(line.pointAt(lo))
        for (i in line.points.indices) {
            if (line.cumulative[i] > lo && line.cumulative[i] < hi) out.add(line.points[i])
        }
        out.add(line.pointAt(hi))
        return out
    }
}
