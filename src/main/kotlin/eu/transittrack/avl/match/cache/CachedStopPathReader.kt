package eu.transittrack.avl.match.cache

import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Component

import eu.transittrack.schedule.model.StopPath
import eu.transittrack.schedule.model.StopPathRepository

/** Revision-scoped, cached view of a trip pattern's ordered stop paths. */
@Component
class CachedStopPathReader(
    private val stopPaths: StopPathRepository,
) {
    @Cacheable(AvlCaches.STOP_PATHS, key = "#revisionId + ':' + #tripPatternId")
    fun orderedByPattern(
        revisionId: Long,
        tripPatternId: Long,
    ): List<StopPath> = stopPaths.findByTripPatternOrdered(revisionId, tripPatternId)
}
