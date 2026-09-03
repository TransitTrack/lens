package eu.transittrack

import java.io.Serializable
import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Mean Earth radius in metres — the same sphere [eu.transittrack.haversineMeters] uses. */
private const val EARTH_RADIUS_M = 6_371_000.0

data class Point(
    val lat: Double,
    val lon: Double,
) {
    /**
     * Straight-line ground distance from this point to [other], in metres.
     *
     * Uses the fast equirectangular approximation (see the file-private `distance`), so it is only
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
    fun matchDistanceAlongVector(v: Vector): Double = matchDistanceAlongVector(this, v)
}

data class Projection(
    val distanceAlong: Double,
    val deviationM: Double,
)

/** A WGS-84 polyline with precomputed cumulative distances (metres) at each vertex. */
class Polyline(
    val points: List<Point>,
) {
    val cumulative: DoubleArray =
        DoubleArray(points.size).also { c ->
            for (i in 1 until points.size) {
                c[i] =
                    c[i - 1] +
                    haversineMeters(
                        points[i - 1].lat,
                        points[i - 1].lon,
                        points[i].lat,
                        points[i].lon,
                    )
            }
        }
    val lengthM: Double
        get() = if (cumulative.isEmpty()) 0.0 else cumulative.last()

    fun project(p: Point): Projection {
        if (points.size == 1) {
            return Projection(0.0, haversineMeters(p.lat, p.lon, points[0].lat, points[0].lon))
        }
        var best = Projection(0.0, Double.MAX_VALUE)
        val cosLat = cos(toRadians(p.lat))

        fun x(pt: Point) = toRadians(pt.lon - p.lon) * cosLat * EARTH_RADIUS_M

        fun y(pt: Point) = toRadians(pt.lat - p.lat) * EARTH_RADIUS_M
        for (i in 1 until points.size) {
            val ax = x(points[i - 1])
            val ay = y(points[i - 1])
            val bx = x(points[i])
            val by = y(points[i])
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

    fun pointAt(distanceAlong: Double): Point {
        if (points.isEmpty()) throw IllegalStateException("empty polyline")
        if (points.size == 1 || distanceAlong <= 0.0) return points.first()
        if (distanceAlong >= lengthM) return points.last()
        var i = 1
        while (i < points.size && cumulative[i] < distanceAlong) i++
        val segFrom = cumulative[i - 1]
        val segTo = cumulative[i]
        val t = if (segTo == segFrom) 0.0 else (distanceAlong - segFrom) / (segTo - segFrom)
        val a = points[i - 1]
        val b = points[i]
        return Point(a.lat + t * (b.lat - a.lat), a.lon + t * (b.lon - a.lon))
    }
}

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
        val distanceInDegreesLongitude = distance / (METERS_PER_DEGREE * cos(Math.toRadians((minLat + maxLat) / 2)))
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

/**
 * A directed segment between two WGS-84 points, treated as a straight line on an equirectangular
 * projection. All lengths and distances are in metres.
 *
 * The projection error is negligible at the scale this is used for (a single shape segment / stop
 * spacing within one transit network) but grows with distance, so a `Vector` should span at most a
 * few kilometres. Endpoints are mutable so callers can walk a polyline by reusing one instance.
 *
 * Geometry helpers come in two flavours:
 *  - [length], [distance], [matchDistanceAlongVector] — point-to-segment queries used for map matching.
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
    fun matchDistanceAlongVector(l: Point): Double = matchDistanceAlongVector(l, this)

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

/**
 * Ground distance between two points, in metres, via the equirectangular ("flat Earth") approximation:
 * project longitude differences with `cos(mean latitude)` and apply Pythagoras. Cheaper than
 * [haversineMeters] and accurate to well under 1% at city scale, but the error grows with distance
 * and near the poles.
 */
private fun distance(
    l1: Point,
    l2: Point,
): Double {
    val lat1 = toRadians(l1.lat)
    val lon1 = toRadians(l1.lon)
    val lat2 = toRadians(l2.lat)
    val lon2 = toRadians(l2.lon)

    val x = (lon2 - lon1) * cos((lat1 + lat2) / 2)
    val y = (lat2 - lat1)
    val d: Double = sqrt(x * x + y * y) * EARTH_RADIUS_M

    return d
}

/**
 * Shortest distance, in metres, from [loc] to [vector]. Backs [Vector.distance] and [Point.distance];
 * see those for the contract. The middle case is solved with the law of cosines to locate the foot
 * of the perpendicular ([v1]), then Pythagoras for the perpendicular's length.
 */
private fun distance(
    loc: Point,
    vector: Vector,
): Double {
    // d1 is distance from the location l to the first location of the vector v
    val d1: Double = distance(loc, vector.l1)
    // d2 is distance from the location l to the second location of the vector v
    val d2: Double = distance(loc, vector.l2)
    // v is length of the vector
    val v: Double = distance(vector.l1, vector.l2)

    // Handle v==0 where we have a zero length vector as a special case
    // so that don't divide by zero and end up with a NaN.
    if (v == 0.0) return d1

    // v1 is the distance from the vector to where the
    // distance to the location is the shortest. It is where a line to
    // the location will be at a right angle to the vector.
    // we get two right angle triangles that split the vector into two
    // distances, v1 and v2. Because these are right angle triangles we know that
    // a^2 + b^2 = c^2, where c is the longer diagonal side of the triangle.
    // This means that we have the following formulas:
    //   v1^2 + d^2 = d1^2
    //   v2^2 + d^2 = d2^2
    //   v1 + v2 = v;
    // If you solve for v1 you will find that it is
    val v1: Double = (sqrd(v) + sqrd(d1) - sqrd(d2)) / (2 * v)

    // We can now determine if the shortest distance
    // from the Location to the Vector is d1, d2, or a right angle line
    // intersecting middle of the Vector. If v1 is negative then the
    // intersection is before the Vector starts and the shortest distance
    // is d1. If v1 is greater than length of v then intersection is
    // beyond the vector and the shortest distance is d2. Otherwise
    // the intersection is in the middle of the vector and can use
    // Pythagorean theorem that a^2 + b^2 = c^2.
    if (v1 <= 0.0) return d1
    if (v1 > v) return d2

    // The shortest distance isn't to one of the end points of the vector.
    // This means that the shortest distance, let's call it d, is a right
    // angle line to somewhere in the middle of the vector. For this situation
    // we get two right angle triangles and can use a^2 + b^2 = c^2.
    var dSquared: Double = sqrd(d1) - sqrd(v1)
    // If started out with a right angle then sqrd(d1) - sqrd(v1) can
    // be slightly negative due to rounding error. If take sqrt() of
    // negative number get NaN when actually want 0.0. Therefore make
    // sure that dSquared not negative.
    if (dSquared < 0.0) dSquared = 0.0

    // Determine and return the shortest distance
    val d = sqrt(dSquared)
    return d
}

/**
 * Distance, in metres, measured along [vector] from its start to the point closest to [loc],
 * clamped to `[0, vector.length()]`. Backs [Vector.matchDistanceAlongVector] and
 * [Point.matchDistanceAlongVector]. Shares the law-of-cosines step with [distance] but returns the
 * offset [v1] instead of the perpendicular distance.
 */
private fun matchDistanceAlongVector(
    loc: Point,
    vector: Vector,
): Double {
    // d1 is distance from the location l to the first location of the vector v
    val d1: Double = distance(loc, vector.l1)
    // d2 is distance from the location l to the second location of the vector v
    val d2: Double = distance(loc, vector.l2)
    // v is length of the vector
    val v: Double = distance(vector.l1, vector.l2)

    // Handle v==0 where we have a zero length vector as a special case
    // so that don't divide by zero and end up with a NaN.
    if (v == 0.0) return 0.0

    // v1 is the distance from the vector to where the
    // distance to the location is the shortest. It is where a line to
    // the location will be at a right angle to the vector.
    // we get two right angle triangles that split the vector into two
    // distances, v1 and v2. Because these are right angle triangles we know that
    // a^2 + b^2 = c^2, where c is the longer diagonal side of the triangle.
    // This means that we have the following formulas:
    //   v1^2 + d^2 = d1^2
    //   v2^2 + d^2 = d2^2
    //   v1 + v2 = v;
    // If you solve for v1 you will find that it is
    val v1: Double = (sqrd(v) + sqrd(d1) - sqrd(d2)) / (2 * v)

    // We can now determine if the shortest distance
    // from the Location to the Vector is d1, d2, or a right angle line
    // intersecting middle of the Vector. If v1 is negative then the
    // intersection is before the Vector starts and the shortest distance
    // is d1. If v1 is greater than length of v then intersection is
    // beyond the vector and the shortest distance is d2. Otherwise
    // the intersection is in the middle of the vector and can use
    // v1 which was already calculated.
    if (v1 <= 0.0) return 0.0
    if (v1 > v) return v
    return v1
}
