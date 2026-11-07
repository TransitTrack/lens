package eu.transittrack.geo

import eu.transittrack.geo.algorithm.calculateCrossProduct
import eu.transittrack.geo.algorithm.calculateDotProduct
import eu.transittrack.geo.algorithm.calculateSquareDist
import eu.transittrack.geo.algorithm.bearingDegrees
import eu.transittrack.geo.algorithm.distance
import eu.transittrack.geo.algorithm.haversineDistance
import eu.transittrack.geo.algorithm.isBetween
import eu.transittrack.geo.algorithm.matchDistanceAlongVector
import kotlin.math.atan2

/**
 * Edge class
 *
 * A directed segment between two WGS-84 positions. Besides the planar predicates used by the
 * polygon algorithms ([isCollinear], [isIntersected]) it offers ground measurements in metres,
 * treating the segment as a straight line on an equirectangular projection.
 *
 * The projection error is negligible at the scale this is used for (a single shape segment / stop
 * spacing within one transit network) but grows with distance, so an `Edge` used for measurements
 * should span at most a few kilometres.
 *
 *  - [length], [distance], [distanceAlongVector] — point-to-segment queries used for map matching.
 *  - [beginning], [end], [middle], [locAlongVector] — sub-segment extraction by distance along the edge.
 *  - [angle], [heading] — orientation, in radians from the equator and in degrees clockwise from north.
 *
 * @property startPosition Edge start point
 * @property endPosition Edge end point
 */
data class Edge(val startPosition: Position, val endPosition: Position) {
    constructor(start: Point, end: Point) : this(start.coordinates, end.coordinates)

    val bearing: Double = bearingDegrees(startPosition.lat, startPosition.lng, endPosition.lat, endPosition.lng)


    /** Length of the segment, in metres. */
    var length: Double = haversineDistance(startPosition.lat, startPosition.lng, endPosition.lat, endPosition.lng)

    /**
     * Shortest ground distance, in metres, from [l] to this segment.
     *
     * Drops a perpendicular from [l] onto the infinite line through the segment. If its foot lands
     * within the segment, the length of that perpendicular is returned; otherwise the distance to
     * the nearer endpoint is returned. A zero-length edge returns the distance to its start.
     */
    fun distance(l: Point): Double = distance(l, this)

    /**
     * Distance, in metres, from the start along this segment to the point closest to [l]. Clamped to
     * `[0, length()]`; a zero-length edge returns `0.0`.
     */
    fun distanceAlongVector(l: Point): Double = matchDistanceAlongVector(l, this)

    /**
     * Orientation of the segment.
     *
     * @param headingInsteadOfAngle when `true`, returns the compass heading (radians clockwise from
     *   north); when `false`, the mathematical angle (radians counterclockwise from due east).
     */
    private fun orientation(headingInsteadOfAngle: Boolean): Double {
        val corner = Position(endPosition.lng, startPosition.lat)
        var xLength = Edge(startPosition, corner).length
        if (endPosition.lng < startPosition.lng) xLength = -xLength

        var yLength = Edge(corner, endPosition).length
        if (endPosition.lat < startPosition.lat) yLength = -yLength

        return if (headingInsteadOfAngle) atan2(xLength, yLength) else atan2(yLength, xLength)
    }

    /**
     * Mathematical angle of the segment in radians, counterclockwise from due east (the equator).
     * Very different from [heading]; range `(-π, π]`.
     */
    fun angle(): Double = orientation(headingInsteadOfAngle = false)

    /**
     * Compass heading of the segment in **degrees** clockwise from due north. Very different from
     * [angle]; range `(-180, 180]`.
     */
    fun heading(): Double = Math.toDegrees(orientation(headingInsteadOfAngle = true))

    /**
     * The point [length] metres along the segment from its start. Linearly interpolates (and
     * extrapolates for values outside `[0, length()]`).
     */
    fun locAlongVector(length: Double): Point = Point(beginning(length).endPosition)

    /**
     * The leading sub-segment `[start .. start + beginningLength]`, i.e. this edge truncated to
     * [beginningLength] metres. A zero-length edge yields a copy of itself.
     */
    fun beginning(beginningLength: Double): Edge = Edge(startPosition, interpolate(ratio(beginningLength)))

    /**
     * The trailing sub-segment `[start + beginningLength .. end]`, i.e. this edge with its first
     * [beginningLength] metres dropped. A zero-length edge yields a copy of itself.
     */
    fun end(beginningLength: Double): Edge = Edge(interpolate(ratio(beginningLength)), endPosition)

    /**
     * The sub-segment between [length1] and [length2] metres along this edge. The result has
     * length `length2 - length1`.
     *
     * @param length1 start offset from the start, in metres
     * @param length2 end offset from the start, in metres (must be `>= length1`)
     */
    fun middle(
        length1: Double,
        length2: Double,
    ): Edge = beginning(length2).end(length1)

    private fun ratio(distance: Double): Double {
        val l = length
        return if (l == 0.0) 0.0 else distance / l
    }

    private fun interpolate(ratio: Double) =
        Position(
            startPosition.lng + ratio * (endPosition.lng - startPosition.lng),
            startPosition.lat + ratio * (endPosition.lat - startPosition.lat),
        )

    /**
     * Determine if a given point is collinear with the points in this edge
     *
     * [Cross-product](https://en.wikipedia.org/wiki/Cross_product) is used
     * to determine if the three points are on the same line.
     */
    fun isCollinear(point: Position): Boolean {
        val (pointLng, pointLat) = point
        val (startLng, startLat) = startPosition
        val (endLng, endLat) = endPosition

        val isCollinear =
            (endLat - startLat) * (pointLng - startLng) == (endLng - startLng) * (pointLat - startLat)
        return isCollinear &&
            isBetween(pointLng, startLng, endLng) &&
            isBetween(pointLat, startLat, endLat)
    }

    /**
     * Determines if two line segments intersect.
     */
    fun isIntersected(otherEdge: Edge): Boolean {
        val otherStartPosition = otherEdge.startPosition
        val otherEndPosition = otherEdge.endPosition
        val crossProduct1 = calculateCrossProduct(otherStartPosition, otherEndPosition, startPosition)
        val crossProduct2 = calculateCrossProduct(otherStartPosition, otherEndPosition, endPosition)

        // handle collinear edges
        if (crossProduct1 == 0.0 && crossProduct2 == 0.0) {
            var distanceToStart = calculateDotProduct(otherStartPosition, otherEndPosition, startPosition)
            var distanceToEnd = calculateDotProduct(otherStartPosition, otherEndPosition, endPosition)
            if (distanceToStart > distanceToEnd) {
                distanceToStart = distanceToEnd.also { distanceToEnd = distanceToStart }
            }
            return 0 <= distanceToEnd &&
                distanceToStart <= calculateSquareDist(otherStartPosition, otherEndPosition)
        }

        val crossProduct3 = calculateCrossProduct(startPosition, endPosition, otherStartPosition)
        val crossProduct4 = calculateCrossProduct(startPosition, endPosition, otherEndPosition)
        return crossProduct1 * crossProduct2 <= 0 && crossProduct3 * crossProduct4 <= 0
    }
}
