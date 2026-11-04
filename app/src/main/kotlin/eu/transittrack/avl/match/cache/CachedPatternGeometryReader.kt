package eu.transittrack.avl.match.cache

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Component

import eu.transittrack.Point
import eu.transittrack.Polyline
import eu.transittrack.avl.match.PatternGeometry

/**
 * Revision-scoped, cached [PatternGeometry] — the *assembled* polyline plus cumulative stop-path
 * distances, so the JSON parse of `stop_path.path_geometry` runs once per (revision, pattern).
 */
@Component
class CachedPatternGeometryReader(
    private val stopPaths: CachedStopPathReader,
    private val json: Json,
) {
    @Cacheable(AvlCaches.PATTERN_GEOMETRY, key = "#revisionId + ':' + #tripPatternId")
    fun geometry(
        revisionId: Long,
        tripPatternId: Long,
    ): PatternGeometry? {
        val paths = stopPaths.orderedByPattern(revisionId, tripPatternId)
        if (paths.isEmpty()) return null
        val pts = ArrayList<Point>()
        val cum = DoubleArray(paths.size)
        val layover = BooleanArray(paths.size)
        var acc = 0.0
        for ((i, sp) in paths.withIndex()) {
            cum[i] = acc
            layover[i] = sp.layoverStop
            acc += sp.lengthM
            val raw = sp.pathGeometry ?: continue

            // Stored as [[lon,lat],...].
            val coords = json.parseToJsonElement(raw).jsonArray
            for (c in coords) {
                val coordinate = c.jsonArray
                val p = Point(coordinate[1].jsonPrimitive.double, coordinate[0].jsonPrimitive.double)
                if (pts.isEmpty() || pts.last() != p) pts.add(p)
            }
        }
        if (pts.size < 2) return null
        return PatternGeometry(Polyline(pts), cum, layover)
    }
}
