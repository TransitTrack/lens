package eu.transittrack.geo.algorithm

import kotlin.math.acos
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

import eu.transittrack.geo.PointCoordinates


// Earth's mean radius in meters (WGS-84 standard approximation)
const val earthRadius = 6371000.0

/**
 * Ground distance between two points, in metres, via the equirectangular ("flat Earth") approximation:
 * project longitude differences with `cos(mean lat)` and apply Pythagoras. Cheaper than
 * [haversineDistance] and accurate to well under 1% at city scale, but the error grows with distance
 * and near the poles.
 */
fun equirectangularDistance(l1: PointCoordinates, l2: PointCoordinates) = equirectangularDistance(l1.lat, l1.lng, l2.lat, l2.lng)

fun equirectangularDistance(p1lat: Double, p1lon: Double, p2lat: Double, p2lon: Double): Double {
    val lat1 = Math.toRadians(p1lat)
    val lon1 = Math.toRadians(p1lon)
    val lat2 = Math.toRadians(p2lat)
    val lon2 = Math.toRadians(p2lon)

    val x = (lon2 - lon1) * cos((lat1 + lat2) / 2)
    val y = (lat2 - lat1)
    val d: Double = sqrt(x * x + y * y) * earthRadius

    return d
}

fun haversineDistance(p1lat: Double, p1lon: Double, p2lat: Double, p2lon: Double): Double {
    val lat1Rad = Math.toRadians(p1lat)
    val lat2Rad = Math.toRadians(p2lat)
    val deltaLat = Math.toRadians(p2lat - p1lat)
    val deltaLon = Math.toRadians(p2lon - p1lon)

    // Haversine formula core calculation
    val a = sin(deltaLat / 2.0).pow(2) +
        cos(lat1Rad) * cos(lat2Rad) * sin(deltaLon / 2.0).pow(2)

    val c = 2.0 * atan2(sqrt(a), sqrt(1.0 - a))

    // Total distance along the sphere surface
    return earthRadius * c
}
/**
 * Calculates the spherical distance between two GeoPoints using the Haversine formula.
 *
 * @param p1 The starting point
 * @param p2 The destination point
 * @return The surface distance between the points in meters
 */
fun haversineDistance(p1: PointCoordinates, p2: PointCoordinates) = haversineDistance(p1.lat, p1.lng, p2.lat, p2.lng)

/**
 * Calculates the geodetic distance between two GeoPoints on the WGS 84 ellipsoid
 * using Lambert's Formula.
 *
 * @param p1 The starting point
 * @param p2 The destination point
 * @return The ground distance between the points in meters
 */
fun lambertDistance(p1: PointCoordinates, p2: PointCoordinates) = lambertDistance(p1.lat, p1.lng, p2.lat, p2.lng)

fun lambertDistance(p1lat: Double, p1lon: Double, p2lat: Double, p2lon: Double): Double {
    // WGS-84 Ellipsoid Constants
    val a = 6378137.0          // Semi-major axis (equatorial radius) in meters
    val f = 1.0 / 298.257223563 // Flattening factor

    // Convert degrees to radians
    val phi1 = Math.toRadians(p1lat)
    val phi2 = Math.toRadians(p2lat)
    val deltaLambda = Math.toRadians(p2lon - p1lon)

    // Step 1: Calculate reduced (parametric) lats (beta)
    val beta1 = atan((1.0 - f) * tan(phi1))
    val beta2 = atan((1.0 - f) * tan(phi2))

    // Step 2: Calculate the central angle (sigma) on the auxiliary sphere
    val sinBeta1 = sin(beta1)
    val cosBeta1 = cos(beta1)
    val sinBeta2 = sin(beta2)
    val cosBeta2 = cos(beta2)
    val cosDeltaLambda = cos(deltaLambda)

    val cosSigma = sinBeta1 * sinBeta2 + cosBeta1 * cosBeta2 * cosDeltaLambda

    // Clamp cosSigma to avoid NaN issues due to floating point inaccuracies
    val clampedCosSigma = cosSigma.coerceIn(-1.0, 1.0)
    val sigma = acos(clampedCosSigma)

    // Handle identical points safely
    if (sigma == 0.0) return 0.0

    // Step 3: Compute Lambert correction components P and Q
    val sinSigma = sin(sigma)

    val p = ((sinBeta1 + sinBeta2).pow(2)) / (1.0 + clampedCosSigma)
    val q = ((sinBeta1 - sinBeta2).pow(2)) / (1.0 - clampedCosSigma)

    // Step 4: Apply the final ellipsoidal distance calculation
    val distance = a * (sigma - (f / 2.0) * (p * sinSigma - q * sigma))

    return distance
}

/** Initial forward azimuth from point 1 to point 2, in compass degrees `[0, 360)` (0 = north). */
fun bearingDegrees(
    lat1: Double,
    lon1: Double,
    lat2: Double,
    lon2: Double,
): Double {
    val phi1 = Math.toRadians(lat1)
    val phi2 = Math.toRadians(lat2)
    val dLon = Math.toRadians(lon2 - lon1)
    val y = sin(dLon) * cos(phi2)
    val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLon)
    return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
}

/** `v` squared. A readability shorthand for the geometry code in [Edge]. */
fun sqrd(v: Double): Double = v.pow(2)
