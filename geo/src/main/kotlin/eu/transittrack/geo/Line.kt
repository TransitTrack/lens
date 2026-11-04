package eu.transittrack.geo

import eu.transittrack.geo.validator.LineStringValidator

typealias LineCoordinates = List<Position>

/**
 * LineString class
 *
 * Class that stores GeoJSON LineString information
 *
 * @property coordinates An Array of Position
 */
data class Line(
    override val coordinates: LineCoordinates,
    override val bbox: BBox? = null,
) : Geometry<LineCoordinates>("LineString") {
    init {
        LineStringValidator.validate(coordinates)
    }

    override fun calculateBBox(): BBox {
        return BBox.from(coordinates)
    }
}
