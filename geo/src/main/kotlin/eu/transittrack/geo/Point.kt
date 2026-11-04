package eu.transittrack.geo

data class Point(
    val lat: Double,
    val lon: Double,
) {
    /**
     * Straight-line ground distance from this point to [other], in metres.
     *
     * Uses the fast equirectangular approximation (see the internal `distance` helper (see `Distance.kt`)), so it is only
     * accurate over short, city-scale spans.
     */
    fun distance(other: Point): Double = distance(this, other)

    /**
     * Shortest ground distance, in metres, from this point to the segment [v] — i.e. the length of
     * the perpendicular dropped onto the segment, or the distance to the nearer endpoint when the
     * foot of that perpendicular falls outside the segment.
     */
    fun distance(v: Vector): Double = distance(this, v)

    /**
     * Distance, in metres, measured **along** [v] from its start ([Vector.l1]) to the point on [v]
     * that lies closest to this point. Clamped to `[0, v.length()]`. Useful for turning a raw GPS
     * fix into a "how far along this shape am I" scalar.
     */
    fun distanceAlongVector(v: Vector): Double = matchDistanceAlongVector(this, v)
}
