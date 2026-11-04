package eu.transittrack.geo

/**
 * BBox class
 *
 * Class that stores GeoJSON BBox information
 *
 * @property minLng Longitude of the most southwesterly point
 * @property minLat Latitude of the most southwesterly point
 * @property maxLng Longitude of the most northeasterly point
 * @property maxLat Latitude of the most northeasterly point
 * @property minAlt Altitude of the most southwesterly point (optional)
 * @property maxAlt Altitude of the most northeasterly point (optional)
 */
data class BBox(
    val minLng: Double,
    val minLat: Double,
    val maxLng: Double,
    val maxLat: Double,
    val minAlt: Double? = null,
    val maxAlt: Double? = null,
) {
    /** Smallest box containing both this box and [other] (altitude bounds are merged when present). */
    fun union(other: BBox): BBox =
        BBox(
            minOf(minLng, other.minLng),
            minOf(minLat, other.minLat),
            maxOf(maxLng, other.maxLng),
            maxOf(maxLat, other.maxLat),
            nullableMin(minAlt, other.minAlt),
            nullableMax(maxAlt, other.maxAlt),
        )

    /**
     * Returns true if [loc] is within [distance] metres of this box. This is not a perfectly
     * accurate calculation: METERS_PER_DEGREE is a constant that ignores how the length of a degree
     * varies with latitude, and latitude and longitude are checked separately. So there is a corner
     * case where each axis is individually within range, so this returns true, but the real
     * distance is further than [distance].
     */
    fun isWithinDistance(
        loc: Point,
        distance: Double,
    ): Boolean {
        val distanceInDegreesLatitude = distance / METERS_PER_DEGREE
        if (minLat > loc.lat + distanceInDegreesLatitude || maxLat < loc.lat - distanceInDegreesLatitude) {
            return false
        }

        // Latitude was OK so check longitude
        val distanceInDegreesLongitude = distance / (METERS_PER_DEGREE * kotlin.math.cos(Math.toRadians((minLat + maxLat) / 2)))
        return !(minLng > loc.lon + distanceInDegreesLongitude) && !(maxLng < loc.lon - distanceInDegreesLongitude)
    }

    companion object {
        // This value is actually dependent on latitude a bit since the earth
        // is not a perfect sphere. But it doesn't vary that much. So using
        // a hard coded value for latitude of 38 degrees, which is approximately
        // San Francisco. For Mexico City at latitude 19 degrees the difference
        // is a bit less than 0.3%, so pretty small for when doing quick
        // calculations.
        private const val METERS_PER_DEGREE = 110996.45

        /**
         * Function to derive BBox from an array of Positions
         */
        fun of(coordinates: LineCoordinates): BBox {
            require(coordinates.isNotEmpty()) {
                "The provided list is empty. Please provide a non-empty list."
            }

            return coordinates
                .asSequence()
                .map { BBox(it.lng, it.lat, it.lng, it.lat, it.alt, it.alt) }
                .reduce(BBox::union)
        }

        fun of(vararg coordinates: PointCoordinates) = of(coordinates.asList())

        /** Bounding box spanning [points]; `null` when [points] is empty. */
        fun of(points: Iterable<Point>): BBox? {
            val positions = points.map { it.coordinates }
            return if (positions.isEmpty()) null else of(positions)
        }

        /** Union of [boxes]; `null` entries are ignored, and `null` is returned when none remain. */
        fun union(boxes: Iterable<BBox?>): BBox? = boxes.filterNotNull().reduceOrNull(BBox::union)

        private fun nullableMin(
            a: Double?,
            b: Double?,
        ): Double? = if (a == null) b else if (b == null) a else minOf(a, b)

        private fun nullableMax(
            a: Double?,
            b: Double?,
        ): Double? = if (a == null) b else if (b == null) a else maxOf(a, b)
    }
}
