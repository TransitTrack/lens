package eu.transittrack.predict.read

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

import org.springframework.stereotype.Service

import eu.transittrack.avl.match.cache.CachedAgencyReader
import eu.transittrack.avl.match.cache.CachedServiceDateReader
import eu.transittrack.avl.model.AvlFeedRepository
import eu.transittrack.gtfs.read.RevisionResolver
import eu.transittrack.predict.PredictionAlgorithm
import eu.transittrack.predict.generate.SCHEDULED_HEADWAY_MARGIN
import eu.transittrack.predict.generate.averageScheduledHeadwaySec
import eu.transittrack.predict.read.dto.HeadwayDto
import eu.transittrack.schedule.model.ScheduleTimeRepository
import eu.transittrack.schedule.model.StopPathRepository
import eu.transittrack.schedule.model.TripPatternRepository

/**
 * Route-level headway/wait-time summary at a stop, derived from the same still-forward-looking
 * predictions [PredictionReadService.stopPredictions] exposes, plus a scheduled-headway baseline:
 * the schedule's own average gap at the stop over the period those predictions cover.
 */
@Service
class HeadwayReadService(
    private val avlFeeds: AvlFeedRepository,
    private val revisionResolver: RevisionResolver,
    private val tripPatternsRepo: TripPatternRepository,
    private val stopPaths: StopPathRepository,
    private val scheduleTimes: ScheduleTimeRepository,
    private val serviceDates: CachedServiceDateReader,
    private val agencies: CachedAgencyReader,
    private val read: PredictionReadService,
    private val clock: Clock,
) {
    fun headway(
        feedCode: String,
        stopId: String,
        routeId: String,
        directionId: Int?,
        algorithm: PredictionAlgorithm? = null,
    ): HeadwayDto {
        val now = clock.instant()
        val arrivals =
            read
                .stopPredictions(feedCode, stopId, routeId, directionId, algorithm)
                .mapNotNull { it.predictedArrival?.let(Instant::parse) }
                .sorted()
        val waitSec =
            arrivals.firstOrNull()?.let {
                Duration
                    .between(now, it)
                    .seconds
                    .toInt()
                    .coerceAtLeast(0)
            }
        val gaps = arrivals.zipWithNext { a, b -> Duration.between(a, b).seconds.toInt() }
        val from = (arrivals.firstOrNull() ?: now).minus(SCHEDULED_HEADWAY_MARGIN)
        val to = (arrivals.lastOrNull() ?: now).plus(SCHEDULED_HEADWAY_MARGIN)
        val scheduledHeadwaySec = computeScheduledHeadway(feedCode, stopId, routeId, directionId, from, to)
        return HeadwayDto(stopId, routeId, directionId, waitSec, gaps, scheduledHeadwaySec)
    }

    /**
     * Average gap between the trips scheduled at the stop within [from]..[to], counting only trips
     * whose service runs that day. Frequency-based trips have no clock times and are left out.
     */
    private fun computeScheduledHeadway(
        feedCode: String,
        stopId: String,
        routeId: String,
        directionId: Int?,
        from: Instant,
        to: Instant,
    ): Int? {
        val feed = avlFeeds.findByCode(feedCode) ?: throw IllegalArgumentException("no avl feed '$feedCode'")
        val revisionId = revisionResolver.resolve(feed.gtfsFeedCode, null)
        val patternIds =
            tripPatternsRepo
                .findByRouteId(revisionId, routeId)
                .filter { directionId == null || it.directionId == directionId }
                .map { it.id!! }
                .toSet()
        val stopTimes =
            stopPaths
                .findByStopId(revisionId, stopId)
                .filter { it.tripPatternId in patternIds }
                .flatMap { scheduleTimes.findTimedAtStopPath(revisionId, it.tripPatternId, it.stopPathIndex) }
        val zone = agencies.timezoneOf(revisionId) ?: ZoneId.systemDefault()
        return averageScheduledHeadwaySec(stopTimes, from, to, zone) { serviceDates.activeServiceIds(revisionId, it) }
    }
}
