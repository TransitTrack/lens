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
 * CRS centred on the line, the resulting [LengthIndexedLine] for distance-along queries, and
 * [planarCumulative]: the running Euclidean (projected-plane) length at each vertex, index-parallel
 * to [ProjectedLine.coordinates] and [ProjectedLine.cumulative]. The two cumulative arrays agree
 * exactly at the representative vertex and closely near it, but diverge — by the Transverse
 * Mercator scale factor's departure from 1 — for vertices far from the central meridian;
 * [planarCumulative] is what lets [ProjectedLine.project]/[ProjectedLine.pointAt] remap a raw
 * planar index back into the geodetic space [ProjectedLine.cumulative] uses.
 */
private class PlanarProjection(
    val forward: MathTransform,
    val inverse: MathTransform,
    val indexedLine: LengthIndexedLine,
    val planarCumulative: DoubleArray,
)

/**
 * WKT for a Transverse Mercator CRS centred exactly at ([centerLon], [centerLat]) with
 * `scale_factor = 1`.
 *
 * Deliberately not one of GeoTools' `AUTO:` WMS codes: `AUTO:42001` (UTM) snaps the central
 * meridian to the nearest standard 6°-wide zone rather than the line's own location, and both
 * `AUTO:42001` and `AUTO:42002` (Transverse Mercator) bake in UTM's `0.9996` scale factor —
 * either introduces metres of error over a long line. Centring the projection on the line itself
 * and leaving the scale factor at `1` removes both of those biases, but Transverse Mercator still
 * only preserves true ellipsoidal distance exactly **along the central meridian** (i.e.
 * north-south through `(centerLon, centerLat)`); distances that run east-west, or anywhere away
 * from that meridian, pick up the usual Transverse Mercator scale-factor growth with distance from
 * it. [ProjectedLine] relies on this projection only for short, local distance-along/deviation
 * queries, and corrects the distance-along component back to geodetic space itself (see
 * [PlanarProjection.planarCumulative]) rather than trusting the raw planar distance.
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
 * consecutive [coordinates], not from the projected plane. [project] and [pointAt] find their
 * raw answer in a local planar projection — a Transverse Mercator CRS centred on the line, see
 * [localTransverseMercatorWkt] — via JTS's [LengthIndexedLine], but then remap that answer's
 * distance-along component back into geodetic space (interpolating within whichever original
 * segment it falls in) before returning it. Without that remap, `distanceAlong` would be exact
 * only for points on or very near the line's central meridian; anywhere else, the Transverse
 * Mercator scale factor's departure from 1 would let it drift outside `[0, lengthM]` — silently
 * breaking array-index use by callers (e.g. binary search over [cumulative]). Only the planar
 * plane's perpendicular deviation ([LineProjection.deviationM]) is left as the direct, unscaled
 * planar answer: it is a local, already-small quantity, not an array index.
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
            val projectedCoordinates = projectedLineString.coordinates
            val planarCumulative =
                DoubleArray(projectedCoordinates.size).also { c ->
                    for (i in 1 until projectedCoordinates.size) {
                        c[i] = c[i - 1] + projectedCoordinates[i - 1].distance(projectedCoordinates[i])
                    }
                }
            PlanarProjection(forward, forward.inverse(), LengthIndexedLine(projectedLineString), planarCumulative)
        } else {
            null
        }

    /**
     * Finds the segment `[i - 1, i]` that index `value` falls into within a cumulative-distance
     * array built the same way [cumulative]/[PlanarProjection.planarCumulative] are (monotonically
     * non-decreasing, index-parallel to [coordinates]), and the fractional position of `value`
     * within that segment, clamped to `[0, 1]`. Shared by [project] (walking [planar]'s
     * [PlanarProjection.planarCumulative] to remap into geodetic space) and [pointAt] (walking
     * [cumulative] to remap into planar space).
     */
    private fun segmentFraction(
        value: Double,
        cumulativeArray: DoubleArray,
    ): Pair<Int, Double> {
        var i = 1
        while (i < cumulativeArray.size - 1 && cumulativeArray[i] < value) i++
        val from = cumulativeArray[i - 1]
        val to = cumulativeArray[i]
        val frac = if (to == from) 0.0 else ((value - from) / (to - from)).coerceIn(0.0, 1.0)
        return i to frac
    }

    /**
     * Projects [p] onto this line: the distance along the line (clamped to `[0, lengthM]`) of the
     * closest point, and the perpendicular deviation from the line, in metres, at that point.
     */
    fun project(p: Coordinate): LineProjection {
        val proj = planar ?: return LineProjection(0.0, geodeticDistanceM(coordinates[0], p))
        val transformedP = JTS.transform(p, null, proj.forward)
        val planarIndex = proj.indexedLine.project(transformedP)
        val onLine = proj.indexedLine.extractPoint(planarIndex)
        val deviationM = transformedP.distance(onLine)
        val (i, frac) = segmentFraction(planarIndex, proj.planarCumulative)
        val distanceAlong = cumulative[i - 1] + frac * (cumulative[i] - cumulative[i - 1])
        return LineProjection(distanceAlong.coerceIn(0.0, lengthM), deviationM)
    }

    /** The coordinate [distanceAlong] metres along this line from its start, clamped to `[0, lengthM]`. */
    fun pointAt(distanceAlong: Double): Coordinate {
        val proj = planar ?: return coordinates[0]
        val clamped = distanceAlong.coerceIn(0.0, lengthM)
        val (i, frac) = segmentFraction(clamped, cumulative)
        val planarIndex = proj.planarCumulative[i - 1] + frac * (proj.planarCumulative[i] - proj.planarCumulative[i - 1])
        val localPoint = proj.indexedLine.extractPoint(planarIndex)
        return JTS.transform(localPoint, null, proj.inverse)
    }
}
