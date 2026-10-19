package eu.transittrack.geo

import org.geotools.api.referencing.operation.MathTransform
import org.geotools.geometry.jts.JTS
import org.geotools.referencing.CRS
import org.geotools.referencing.crs.DefaultGeographicCRS
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.linearref.LengthIndexedLine

/**
 * Result of projecting a point onto a [ProjectedLine]: how far along the line ([distanceAlong],
 * metres from its start) the closest point sits, and how far off the line ([deviationM], metres,
 * perpendicular) the projected point actually was.
 */
data class LineProjection(
    val distanceAlong: Double,
    val deviationM: Double,
)

/**
 * Holds the local planar projection machinery for a [ProjectedLine] with two or more
 * [coordinates] — the forward/inverse transforms between WGS-84 and a local Transverse Mercator
 * CRS centred on the line, and the resulting [LengthIndexedLine] for distance-along queries.
 */
private class PlanarProjection(
    val forward: MathTransform,
    val inverse: MathTransform,
    val indexedLine: LengthIndexedLine,
)

/**
 * WKT for a Transverse Mercator CRS centred exactly at ([centerLon], [centerLat]) with
 * `scale_factor = 1`.
 *
 * Deliberately not one of GeoTools' `AUTO:` WMS codes: `AUTO:42001` (UTM) snaps the central
 * meridian to the nearest standard 6°-wide zone rather than the line's own location, and both
 * `AUTO:42001` and `AUTO:42002` (Transverse Mercator) bake in UTM's `0.9996` scale factor —
 * either introduces metres of error over a long line. Centring the projection on the line itself
 * and leaving the scale factor at `1` makes this an honest local tangent plane: ellipsoidal
 * (WGS-84) distances through the centre, in any direction, come back unscaled.
 */
private fun localTransverseMercatorWkt(
    centerLon: Double,
    centerLat: Double,
): String =
    """
    PROJCS["Local Transverse Mercator",
      GEOGCS["WGS 84",
        DATUM["WGS_1984", SPHEROID["WGS_1984", 6378137, 298.257223563]],
        PRIMEM["Greenwich", 0],
        UNIT["Decimal_Degree", 0.0174532925199433]],
      PROJECTION["Transverse_Mercator"],
      PARAMETER["Latitude_of_Origin", $centerLat],
      PARAMETER["Central_Meridian", $centerLon],
      PARAMETER["False_Easting", 0],
      PARAMETER["False_Northing", 0],
      PARAMETER["Scale_Factor", 1.0],
      UNIT["Meter", 1]]
    """.trimIndent()

/**
 * A WGS-84 polyline ([coordinates]) that supports projecting arbitrary points onto itself and
 * extracting points by distance along it.
 *
 * [cumulative] and [lengthM] are geodetically exact: computed from [geodeticDistanceM] between
 * consecutive [coordinates], not from the projected plane. [project] and [pointAt] instead work
 * in a local planar projection — a Transverse Mercator CRS centred on the line, see
 * [localTransverseMercatorWkt] — via JTS's [LengthIndexedLine]; distortion is negligible at the
 * scale this is used for (a single shape / stop spacing within one transit network).
 */
class ProjectedLine(
    val coordinates: List<Coordinate>,
) {
    val cumulative: DoubleArray =
        DoubleArray(coordinates.size).also { c ->
            for (i in 1 until coordinates.size) {
                c[i] = c[i - 1] + geodeticDistanceM(coordinates[i - 1], coordinates[i])
            }
        }

    val lengthM: Double
        get() = if (cumulative.isEmpty()) 0.0 else cumulative.last()

    private val planar: PlanarProjection? =
        if (coordinates.size >= 2) {
            val representative = coordinates[coordinates.size / 2]
            val localCrs = CRS.parseWKT(localTransverseMercatorWkt(representative.x, representative.y))
            val forward = CRS.findMathTransform(DefaultGeographicCRS.WGS84, localCrs)
            val lineString = GeometryFactory().createLineString(coordinates.toTypedArray())
            val projectedLineString = JTS.transform(lineString, forward)
            PlanarProjection(forward, forward.inverse(), LengthIndexedLine(projectedLineString))
        } else {
            null
        }

    /**
     * Projects [p] onto this line: the distance along the line (clamped to `[0, lengthM]`) of the
     * closest point, and the perpendicular deviation from the line, in metres, at that point.
     */
    fun project(p: Coordinate): LineProjection {
        val proj = planar ?: return LineProjection(0.0, geodeticDistanceM(coordinates[0], p))
        val transformedP = JTS.transform(p, null, proj.forward)
        val index = proj.indexedLine.project(transformedP)
        val onLine = proj.indexedLine.extractPoint(index)
        return LineProjection(index, transformedP.distance(onLine))
    }

    /** The coordinate [distanceAlong] metres along this line from its start. */
    fun pointAt(distanceAlong: Double): Coordinate {
        val proj = planar ?: return coordinates[0]
        val localPoint = proj.indexedLine.extractPoint(distanceAlong)
        return JTS.transform(localPoint, null, proj.inverse)
    }
}
