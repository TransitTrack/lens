package eu.transittrack.avl.match.cache

import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Component

import eu.transittrack.avl.match.PatternGeometry
import eu.transittrack.schedule.model.TripPatternRepository

/**
 * Revision-scoped, cached [PatternGeometry]: the pattern's stored polyline plus the cumulative
 * stop-path distances (from `stop_path.length_m`), so the derivation runs once per (revision, pattern).
 */
@Component
class CachedPatternGeometryReader(
    private val stopPaths: CachedStopPathReader,
    private val patterns: TripPatternRepository,
) {
    @Cacheable(AvlCaches.PATTERN_GEOMETRY, key = "#revisionId + ':' + #tripPatternId")
    fun geometry(
        revisionId: Long,
        tripPatternId: Long,
    ): PatternGeometry? {
        val paths = stopPaths.orderedByPattern(revisionId, tripPatternId)
        if (paths.isEmpty()) return null
        val line = patterns.findById(tripPatternId)?.geometry ?: return null
        val cum = DoubleArray(paths.size)
        val layover = BooleanArray(paths.size)
        var acc = 0.0
        for ((i, sp) in paths.withIndex()) {
            cum[i] = acc
            layover[i] = sp.layoverStop
            acc += sp.lengthM
        }
        return PatternGeometry(line, cum, layover)
    }
}
