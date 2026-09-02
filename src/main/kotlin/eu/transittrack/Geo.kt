package eu.transittrack

import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import jakarta.persistence.Column
import jakarta.persistence.Embeddable

/** Mean Earth radius in metres — the same sphere [eu.transittrack.haversineMeters] uses. */
private const val EARTH_RADIUS_M = 6_371_000.0

data class Point(val lat: Double, val lon: Double)

data class Projection(val distanceAlong: Double, val deviationM: Double)

/** A WGS-84 polyline with precomputed cumulative distances (metres) at each vertex. */
class Polyline(val points: List<Point>) {
    val cumulative: DoubleArray = DoubleArray(points.size).also { c ->
        for (i in 1 until points.size) {
            c[i] = c[i - 1] + haversineMeters(
                points[i - 1].lat, points[i - 1].lon, points[i].lat, points[i].lon,
            )
        }
    }
    val lengthM: Double get() = if (cumulative.isEmpty()) 0.0 else cumulative.last()

    fun project(p: Point): Projection {
        if (points.size == 1) {
            return Projection(0.0, haversineMeters(p.lat, p.lon, points[0].lat, points[0].lon))
        }
        var best = Projection(0.0, Double.MAX_VALUE)
        val cosLat = cos(toRadians(p.lat))
        fun x(pt: Point) = toRadians(pt.lon - p.lon) * cosLat * EARTH_RADIUS_M
        fun y(pt: Point) = toRadians(pt.lat - p.lat) * EARTH_RADIUS_M
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

    fun pointAt(distanceAlong: Double): Point {
        if (points.isEmpty()) throw IllegalStateException("empty polyline")
        if (points.size == 1 || distanceAlong <= 0.0) return points.first()
        if (distanceAlong >= lengthM) return points.last()
        var i = 1
        while (i < points.size && cumulative[i] < distanceAlong) i++
        val segFrom = cumulative[i - 1]; val segTo = cumulative[i]
        val t = if (segTo == segFrom) 0.0 else (distanceAlong - segFrom) / (segTo - segFrom)
        val a = points[i - 1]; val b = points[i]
        return Point(a.lat + t * (b.lat - a.lat), a.lon + t * (b.lon - a.lon))
    }
}


@Embeddable
class Extent{

    // Initialized to NaN to represent "unset" values safely without
    // throwing SQL errors regarding Infinity database inserts
    @Column(name = "min_lat")
    final var minLat: Double = Double.NaN
        private set

    @Column(name = "max_lat")
    final var maxLat: Double = Double.NaN
        private set

    @Column(name = "min_lon")
    final var minLon: Double = Double.NaN
        private set

    @Column(name = "max_lon")
    final var maxLon: Double = Double.NaN
        private set

    /**
     * Once an Extent has been constructed need to simply add associated Locations (or Extents).
     * Once all locations have been added the Extent will be the rectangle spanning all of those
     * Locations and Extents.
     *
     * @param l
     */
    fun add(l: Point) {
        if (minLat.isNaN()) {
            minLat = l.lat; maxLat = l.lat
            minLon = l.lon; maxLon = l.lon
            return
        }
        if (l.lat < minLat) minLat = l.lat
        if (l.lat > maxLat) maxLat = l.lat
        if (l.lon < minLon) minLon = l.lon
        if (l.lon > maxLon) maxLon = l.lon
    }

    fun add(e: Extent) {
        if (e.minLat.isNaN())
            return
        if (this.minLat.isNaN()) {
            this.minLat = e.minLat; this.maxLat = e.maxLat
            this.minLon = e.minLon; this.maxLon = e.maxLon
            return
        }
        if (e.minLat < minLat) minLat = e.minLat
        if (e.maxLat > maxLat) maxLat = e.maxLat
        if (e.minLon < minLon) minLon = e.minLon
        if (e.maxLon > maxLon) maxLon = e.maxLon
    }

    /**
     * Returns true if the location is with the specified distance of this extent. This is not a
     * perfectly accurate calculation due to METERS_PER_DEGREE being a constant and not taking into
     * account changes in diameter of the earth depending on latitude. Also, looks at latitude and
     * longitude separately. So there is a corner case where latitude and longitude might be OK
     * individually, so the method returns true, but together the distance would be actually be
     * further away then the specified distance.
     *
     * @param loc
     * @param distance
     * @return
     */
    fun isWithinDistance(loc: Point, distance: Double): Boolean {
        if (minLat.isNaN()) return false

        val distanceInDegreesLatitude = distance / METERS_PER_DEGREE
        if (minLat > loc.lat + distanceInDegreesLatitude || maxLat < loc.lat - distanceInDegreesLatitude)
            return false

        // Latitude was OK so check longitude
        val distanceInDegreesLongitude = distance / (METERS_PER_DEGREE * cos(Math.toRadians((minLat + maxLat) / 2)))
        return !(minLon > loc.lon + distanceInDegreesLongitude) &&
                !(maxLon < loc.lon - distanceInDegreesLongitude)
    }

    companion object {
        // This value is actually dependent on latitude a bit since the earth
        // is not a perfect sphere. But it doesn't vary that much. So using
        // a hard coded value for latitude of 38 degrees, which is approximately
        // San Francisco. For Mexico City at latitude 19 degrees the difference
        // is a bit less than 0.3%, so pretty small for when doing quick
        // calculations.
        private const val METERS_PER_DEGREE = 110996.45
    }
}