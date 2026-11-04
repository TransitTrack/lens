package eu.transittrack.geo

import java.io.Serializable
import kotlin.math.atan2

/**
 * A directed segment between two WGS-84 points, treated as a straight line on an equirectangular
 * projection. All lengths and distances are in metres.
 *
 * The projection error is negligible at the scale this is used for (a single shape segment / stop
 * spacing within one transit network) but grows with distance, so a `Vector` should span at most a
 * few kilometres. Endpoints are mutable so callers can walk a polyline by reusing one instance.
 *
 * Geometry helpers come in two flavours:
 *  - [length], [distance], [distanceAlongVector] — point-to-segment queries used for map matching.
 *  - [beginning], [end], [middle], [locAlongVector] — sub-segment extraction by distance along the vector.
 *  - [angle], [heading] — orientation, in radians from the equator and in degrees clockwise from north.
 */
class Vector(
    var l1: Point,
    var l2: Point,
) : Serializable {
    /** Length of the segment, in metres. */
    fun length(): Double = l1.distance(l2)

    /**
     * Shortest ground distance, in metres, from [l] to this segment.
     *
     * Drops a perpendicular from [l] onto the infinite line through the segment. If its foot lands
     * within the segment, the length of that perpendicular is returned; otherwise the distance to
     * the nearer endpoint ([l1] or [l2]) is returned. A zero-length vector returns the distance to
     * [l1].
     */
    fun distance(l: Point): Double = distance(l, this)

    /**
     * Distance, in metres, from [l1] along this segment to the point closest to [l]. Clamped to
     * `[0, length()]`; a zero-length vector returns `0.0`.
     */
    fun distanceAlongVector(l: Point): Double = matchDistanceAlongVector(l, this)

    /**
     * Orientation of the segment.
     *
     * @param headingInsteadOfAngle when `true`, returns the compass heading (radians clockwise from
     *   north); when `false`, the mathematical angle (radians counterclockwise from due east).
     */
    private fun orientation(headingInsteadOfAngle: Boolean): Double {
        val vx = Vector(l1, Point(l1.lat, l2.lon))
        var xLength = vx.length()
        if (l2.lon < l1.lon) xLength = -xLength

        val vy = Vector(Point(l1.lat, l2.lon), l2)
        var yLength = vy.length()
        if (l2.lat < l1.lat) yLength = -yLength

        // Return either the heading or the angle
        if (headingInsteadOfAngle) {
            return atan2(xLength, yLength) // heading
        } else {
            return atan2(yLength, xLength) // angle
        }
    }

    /**
     * Mathematical angle of the segment in radians, counterclockwise from due east (the equator).
     * Very different from [heading]; range `(-π, π]`.
     */
    fun angle(): Double {
        val headingInsteadOfAngle = false
        return orientation(headingInsteadOfAngle)
    }

    /**
     * Compass heading of the segment in **degrees** clockwise from due north. Very different from
     * [angle]; range `(-180, 180]`.
     */
    fun heading(): Double {
        val headingInsteadOfAngle = true
        return Math.toDegrees(orientation(headingInsteadOfAngle))
    }

    /**
     * The point [length] metres along the segment from [l1]. Linearly interpolates (and extrapolates
     * for values outside `[0, length()]`).
     */
    fun locAlongVector(length: Double): Point {
        val beginningVector = beginning(length)
        return beginningVector.l2
    }

    /**
     * The leading sub-segment `[l1 .. l1 + beginningLength]`, i.e. this vector truncated to
     * [beginningLength] metres. A zero-length vector yields a copy of itself.
     *
     * @param beginningLength length, in metres, of the sub-segment to return
     */
    fun beginning(beginningLength: Double): Vector {
        val l = length()
        val ratio = if (l == 0.0) 0.0 else beginningLength / length()
        val newL2 = Point(
            l1.lat + ratio * (l2.lat - l1.lat), l1.lon + ratio * (l2.lon - l1.lon),
        )
        return Vector(l1, newL2)
    }

    /**
     * The trailing sub-segment `[l1 + beginningLength .. l2]`, i.e. this vector with its first
     * [beginningLength] metres dropped. A zero-length vector yields a copy of itself.
     *
     * @param beginningLength length, in metres, to skip from the start
     */
    fun end(beginningLength: Double): Vector {
        val l = length()
        val ratio = if (l == 0.0) 0.0 else beginningLength / length()
        val newL1 = Point(
            l1.lat + ratio * (l2.lat - l1.lat), l1.lon + ratio * (l2.lon - l1.lon),
        )
        return Vector(newL1, l2)
    }

    /**
     * The sub-segment between [length1] and [length2] metres along this vector. The result has
     * length `length2 - length1`.
     *
     * @param length1 start offset from [l1], in metres
     * @param length2 end offset from [l1], in metres (must be `>= length1`)
     */
    fun middle(
        length1: Double,
        length2: Double,
    ): Vector {
        val beginningVector = beginning(length2)
        return beginningVector.end(length1)
    }

    override fun toString(): String = "Vector [" + "l1=" + l1 + ", l2=" + l2 + ", length=" + length() + "]"
}
