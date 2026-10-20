package eu.transittrack.avl.match.cache

import org.locationtech.jts.geom.Coordinate
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper

import eu.transittrack.avl.match.PatternGeometry
import eu.transittrack.geo.ProjectedLine

/**
 * Revision-scoped, cached [PatternGeometry] — the *assembled* polyline plus cumulative stop-path
 * distances, so the JSON parse of `stop_path.path_geometry` runs once per (revision, pattern).
 */
@Component
class CachedPatternGeometryReader(
    private val stopPaths: CachedStopPathReader,
    private val json: JsonMapper,
) {
    private val log = LoggerFactory.getLogger(CachedPatternGeometryReader::class.java)

    @Cacheable(AvlCaches.PATTERN_GEOMETRY, key = "#revisionId + ':' + #tripPatternId")
    fun geometry(
        revisionId: Long,
        tripPatternId: Long,
    ): PatternGeometry? {
        val paths = stopPaths.orderedByPattern(revisionId, tripPatternId)
        if (paths.isEmpty()) return null
        val pts = ArrayList<Coordinate>()
        val cum = DoubleArray(paths.size)
        val layover = BooleanArray(paths.size)
        var acc = 0.0
        for ((i, sp) in paths.withIndex()) {
            cum[i] = acc
            layover[i] = sp.layoverStop
            acc += sp.lengthM
            val raw = sp.pathGeometry ?: continue

            // Stored as [[lon,lat],...]; parsed loosely, mirroring ScheduleReadService.parseGeometry.
            @Suppress("UNCHECKED_CAST")
            val coords = json.readValue(raw, List::class.java) as List<List<Number>>
            for (c in coords) {
                // Already [lon, lat]: Coordinate(x = lon, y = lat) takes c[0]/c[1] directly, no swap.
                val p = Coordinate(c[0].toDouble(), c[1].toDouble())
                if (pts.isEmpty() || pts.last() != p) pts.add(p)
            }
        }
        if (pts.size < 2) return null
        // path_geometry is feed-controlled; a degenerate shape could make the CRS/transform setup
        // inside ProjectedLine throw. Fail soft here (same null contract as the size check above)
        // rather than let an exception escape this @Cacheable method into the match loop.
        return runCatching { ProjectedLine(pts) }
            .onFailure { e -> log.warn("Failed to build ProjectedLine for pattern {} (revision {})", tripPatternId, revisionId, e) }
            .getOrNull()
            ?.let { PatternGeometry(it, cum, layover) }
    }
}
