package eu.transittrack.geo

import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

import eu.transittrack.geo.validator.LineStringValidator

typealias LineCoordinates = List<Position>

/**
 * LineString class
 *
 * Class that stores GeoJSON LineString information, plus WGS-84 polyline operations (cumulative
 * distances, projecting a point onto the line, locating a point and slicing by distance).
 *
 * @property coordinates An Array of Position (at least two)
 */
data class Line(
    override val coordinates: LineCoordinates,
    override val bbox: BBox? = null,
) : Geometry<LineCoordinates>("LineString") {
    init {
        LineStringValidator.validate(coordinates)
    }

    /** Cumulative haversine distance (metres) from the start to each vertex; `[0] == 0.0`. Computed on first use. */
    val cumulative: DoubleArray by lazy {
        DoubleArray(coordinates.size).also { c ->
            for (i in 1 until coordinates.size) {
                val a = coordinates[i - 1]
                val b = coordinates[i]
                c[i] = c[i - 1] + haversineDistance(a.lat, a.lng, b.lat, b.lng)
            }
        }
    }

    val size: Int get() = coordinates.size

    /** Total length of the line, in metres. */
    val lengthM: Double get() = cumulative.last()

    /**
     * Projects [p] onto the line: the distance along it of the closest point, and how far [p] is
     * from that point (both in metres).
     */
    fun project(p: Point): Projection {
        var best = Projection(0.0, Double.MAX_VALUE)
        val cosLat = cos(Math.toRadians(p.lat))

        fun x(pos: Position) = Math.toRadians(pos.lng - p.lon) * cosLat * earthRadius

        fun y(pos: Position) = Math.toRadians(pos.lat - p.lat) * earthRadius

        for (i in 1 until coordinates.size) {
            val ax = x(coordinates[i - 1])
            val ay = y(coordinates[i - 1])
            val bx = x(coordinates[i])
            val by = y(coordinates[i])
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

    /** The point [distanceAlong] metres from the start, clamped to the ends of the line. */
    fun pointAt(distanceAlong: Double): Point {
        if (distanceAlong <= 0.0) return coordinates.first().toPoint()
        if (distanceAlong >= lengthM) return coordinates.last().toPoint()
        var i = 1
        while (i < coordinates.size && cumulative[i] < distanceAlong) i++
        val segFrom = cumulative[i - 1]
        val segTo = cumulative[i]
        val t = if (segTo == segFrom) 0.0 else (distanceAlong - segFrom) / (segTo - segFrom)
        val a = coordinates[i - 1]
        val b = coordinates[i]
        return Point(a.lat + t * (b.lat - a.lat), a.lng + t * (b.lng - a.lng))
    }

    /** The part of the line between [fromM] and [toM] metres from the start, keeping the vertices in between. */
    fun slice(
        fromM: Double,
        toM: Double,
    ): Line {
        val lo = max(0.0, min(fromM, toM))
        val hi = min(lengthM, max(fromM, toM))
        val out = ArrayList<Position>()
        out.add(pointAt(lo).coordinates)
        for (i in coordinates.indices) {
            if (cumulative[i] > lo && cumulative[i] < hi) out.add(coordinates[i])
        }
        out.add(pointAt(hi).coordinates)
        return Line(out)
    }

    override fun calculateBBox(): BBox = BBox.of(coordinates)

    companion object {
        fun of(points: List<Point>): Line = Line(points.map { it.coordinates })

        fun of(vararg points: Point): Line = of(points.asList())

        private fun Position.toPoint() = Point(this)
    }
}
