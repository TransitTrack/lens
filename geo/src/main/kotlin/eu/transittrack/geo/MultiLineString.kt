package eu.transittrack.geo

import eu.transittrack.geo.validator.LineStringValidator

typealias MultiLineStringCoordinates = List<List<Position>>

/**
 * MultiLineString class
 *
 * Class that stores GeoJSON MultiLineString information
 *
 * @property coordinates An Array of Position arrays
 */
data class MultiLineString(
    override val coordinates: MultiLineStringCoordinates,
    override val bbox: BBox? = null,
) : Geometry<MultiLineStringCoordinates>("MultiLineString") {
    init {
        coordinates.forEach {
            LineStringValidator.validate(it)
        }
    }
    constructor(vararg lines: Line) :
        this(
            lines.map { it.coordinates },
            BBox.of(lines.flatMap { it.coordinates }),
        )

    /**
     * Split MultiLineString into LineString lists.
     */
    fun split(): List<Line> {
        return coordinates.map {
            Line(it, BBox.of(it))
        }
    }

    /**
     * Returns a new MultiLineString with the passed LineString appended.
     */
    fun added(vararg lines: Line): MultiLineString {
        val newCoordinates: List<List<Position>> = coordinates + lines.map { it.coordinates }
        val newBBox = BBox.of(newCoordinates.flatten())
        return MultiLineString(newCoordinates, newBBox)
    }

    override fun calculateBBox(): BBox {
        return BBox.of(coordinates.flatten())
    }
}
