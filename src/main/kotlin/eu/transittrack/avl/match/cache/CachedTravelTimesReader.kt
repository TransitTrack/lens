package eu.transittrack.avl.match.cache

import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Component

import eu.transittrack.schedule.model.TravelTimesForStopPath
import eu.transittrack.schedule.model.TravelTimesForStopPathRepository

/**
 * Revision-scoped, cached schedule travel/dwell times of a trip pattern's stop paths, by stop path
 * index - the seed every prediction and learning step falls back to, read several times per match.
 */
@Component
class CachedTravelTimesReader(
    private val travelTimes: TravelTimesForStopPathRepository,
) {
    @Cacheable(AvlCaches.TRAVEL_TIMES, key = "#revisionId + ':' + #tripPatternId")
    fun byStopPath(
        revisionId: Long,
        tripPatternId: Long,
    ): Map<Int, TravelTimesForStopPath> = travelTimes.findByTripPatternOrdered(revisionId, tripPatternId).associateBy { it.stopPathIndex }
}
