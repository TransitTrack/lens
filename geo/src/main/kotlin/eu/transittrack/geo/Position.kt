package eu.transittrack.geo

import eu.transittrack.geo.validator.PositionValidator

/**
 * Position class
 *
 * Class that stores GeoJSON Position information
 *
 * @property lng Longitude
 * @property lat Latitude
 * @property alt Altitude (optional)
 */
data class Position(
    val lng: Double,
    val lat: Double,
    val alt: Double? = null,
) {
    init {
        PositionValidator.validate(this)
    }
}
