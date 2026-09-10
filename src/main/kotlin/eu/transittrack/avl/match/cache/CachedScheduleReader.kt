package eu.transittrack.avl.match.cache

import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Component

import eu.transittrack.avl.match.SchedulePoint
import eu.transittrack.schedule.model.ScheduleTimeRepository

/** Revision-scoped, cached ordered schedule points for a trip (row id). */
@Component
class CachedScheduleReader(
    private val scheduleTimes: ScheduleTimeRepository,
) {
    @Cacheable(AvlCaches.SCHEDULE, key = "#revisionId + ':' + #tripRowId")
    fun orderedByTrip(
        revisionId: Long,
        tripRowId: Long,
    ): List<SchedulePoint> =
        scheduleTimes.findByTripOrdered(revisionId, tripRowId).map {
            SchedulePoint(it.stopPathIndex, it.arrivalSec, it.departureSec)
        }
}
