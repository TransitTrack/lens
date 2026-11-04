package eu.transittrack.geo

import kotlin.math.sqrt


internal fun distance(l1: Point, l2: Point) = haversineDistance(l1, l2)

/**
 * Shortest distance, in metres, from [loc] to [vector]. Backs [Vector.distance] and [Point.distance];
 * see those for the contract. The middle case is solved with the law of cosines to locate the foot
 * of the perpendicular ([v1]), then Pythagoras for the perpendicular's length.
 */
internal fun distance(loc: Point, vector: Vector): Double {
    // v is length of the vector
    val v: Double = distance(vector.l1, vector.l2)
    // d1 is distance from the location l to the first location of the vector v
    val d1: Double = distance(loc, vector.l1)

    // Handle v==0 where we have a zero length vector as a special case
    // so that don't divide by zero and end up with a NaN.
    if (v == 0.0) return d1

    // d2 is distance from the location l to the second location of the vector v
    val d2: Double = distance(loc, vector.l2)

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
 * clamped to `[0, vector.length()]`. Backs [Vector.distanceAlongVector] and
 * [Point.matchDistanceAlongVector]. Shares the law-of-cosines step with [distance] but returns the
 * offset [v1] instead of the perpendicular distance.
 */
internal fun matchDistanceAlongVector(loc: Point, vector: Vector): Double {
    // v is length of the vector
    val v: Double = distance(vector.l1, vector.l2)

    // Handle v==0 where we have a zero length vector as a special case
    // so that don't divide by zero and end up with a NaN.
    if (v == 0.0) return 0.0

    // d1 is distance from the location l to the first location of the vector v
    val d1: Double = distance(loc, vector.l1)
    // d2 is distance from the location l to the second location of the vector v
    val d2: Double = distance(loc, vector.l2)


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
