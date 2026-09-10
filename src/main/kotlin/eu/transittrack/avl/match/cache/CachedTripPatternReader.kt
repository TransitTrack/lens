package eu.transittrack.avl.match.cache

import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Component

import eu.transittrack.schedule.model.TripPattern
import eu.transittrack.schedule.model.TripPatternRepository

/** Revision-scoped, cached trip-pattern lookup. The spatial-extent predicate stays with the caller. */
@Component
class CachedTripPatternReader(
    private val patterns: TripPatternRepository,
) {
    @Cacheable(AvlCaches.TRIP_PATTERN, key = "#revisionId + ':' + #tripPatternId")
    fun byId(
        revisionId: Long,
        tripPatternId: Long,
    ): TripPattern? = patterns.findById(tripPatternId).orElse(null)
}
