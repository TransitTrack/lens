package eu.transittrack.avl.match.cache

import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper

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
    private val json: JsonMapper,
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
        var acc = 0.0
        for ((i, sp) in paths.withIndex()) {
            cum[i] = acc
            acc += sp.lengthM
            val raw = sp.pathGeometry ?: continue

            // Stored as [[lon,lat],...]; parsed loosely, mirroring ScheduleReadService.parseGeometry.
            @Suppress("UNCHECKED_CAST")
            val coords = json.readValue(raw, List::class.java) as List<List<Number>>
            for (c in coords) {
                val p = Point(c[1].toDouble(), c[0].toDouble())
                if (pts.isEmpty() || pts.last() != p) pts.add(p)
            }
        }
        if (pts.size < 2) return null
        return PatternGeometry(Polyline(pts), cum)
    }
}
