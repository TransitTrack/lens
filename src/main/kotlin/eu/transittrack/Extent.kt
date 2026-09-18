package eu.transittrack

import jakarta.persistence.Column
import jakarta.persistence.Embeddable

@Embeddable
class Extent {
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
     * Once an Extent has been constructed need to simply add associated Locations (or Extents). Once
     * all locations have been added the Extent will be the rectangle spanning all of those Locations
     * and Extents.
     *
     * @param l
     */
    fun add(l: Point) {
        if (minLat.isNaN()) {
            minLat = l.lat
            maxLat = l.lat
            minLon = l.lon
            maxLon = l.lon
            return
        }
        if (l.lat < minLat) minLat = l.lat
        if (l.lat > maxLat) maxLat = l.lat
        if (l.lon < minLon) minLon = l.lon
        if (l.lon > maxLon) maxLon = l.lon
    }

    fun add(e: Extent) {
        if (e.minLat.isNaN()) {
            return
        }
        if (this.minLat.isNaN()) {
            this.minLat = e.minLat
            this.maxLat = e.maxLat
            this.minLon = e.minLon
            this.maxLon = e.maxLon
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
    fun isWithinDistance(
        loc: Point,
        distance: Double,
    ): Boolean {
        if (minLat.isNaN()) return false

        val distanceInDegreesLatitude = distance / METERS_PER_DEGREE
        if (minLat > loc.lat + distanceInDegreesLatitude || maxLat < loc.lat - distanceInDegreesLatitude) {
            return false
        }

        // Latitude was OK so check longitude
        val distanceInDegreesLongitude = distance / (METERS_PER_DEGREE * kotlin.math.cos(Math.toRadians((minLat + maxLat) / 2)))
        return !(minLon > loc.lon + distanceInDegreesLongitude) && !(maxLon < loc.lon - distanceInDegreesLongitude)
    }

    /** True while no point or extent has been added (the bounding box is unset). */
    final val isEmpty: Boolean
        get() = minLat.isNaN()

    companion object {
        // This value is actually dependent on latitude a bit since the earth
        // is not a perfect sphere. But it doesn't vary that much. So using
        // a hard coded value for latitude of 38 degrees, which is approximately
        // San Francisco. For Mexico City at latitude 19 degrees the difference
        // is a bit less than 0.3%, so pretty small for when doing quick
        // calculations.
        private const val METERS_PER_DEGREE = 110996.45

        /** Bounding box spanning [points]; empty when [points] is empty. */
        fun of(points: Iterable<Point>): Extent = Extent().apply { points.forEach(::add) }

        /** Union of [extents]; empty ones are ignored. */
        fun ofExtents(extents: Iterable<Extent>): Extent = Extent().apply { extents.forEach(::add) }
    }
}
