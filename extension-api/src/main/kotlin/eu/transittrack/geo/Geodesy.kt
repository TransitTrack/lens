package eu.transittrack.geo

import org.geotools.referencing.GeodeticCalculator
import org.geotools.referencing.datum.DefaultEllipsoid
import org.locationtech.jts.geom.Coordinate

/**
 * Geodetic (ellipsoidal, WGS-84) distance between [a] and [b], in metres.
 *
 * Every [Coordinate] in this codebase is `(x = longitude, y = latitude)`, which matches
 * [GeodeticCalculator]'s own `(longitude, latitude)` argument order directly — no swap needed.
 */
fun geodeticDistanceM(
    a: Coordinate,
    b: Coordinate,
): Double {
    val calc = GeodeticCalculator(DefaultEllipsoid.WGS84)
    calc.setStartingGeographicPoint(a.x, a.y)
    calc.setDestinationGeographicPoint(b.x, b.y)
    return calc.orthodromicDistance
}

/**
 * Initial forward geodetic azimuth from [a] to [b], in compass degrees `[0, 360)` (0 = north,
 * 90 = east). Same convention as the old top-level `bearingDegrees`.
 */
fun geodeticBearingDegrees(
    a: Coordinate,
    b: Coordinate,
): Double {
    val calc = GeodeticCalculator(DefaultEllipsoid.WGS84)
    calc.setStartingGeographicPoint(a.x, a.y)
    calc.setDestinationGeographicPoint(b.x, b.y)
    return (calc.azimuth + 360.0) % 360.0
}
