package eu.transittrack.predict.read

import java.time.Duration
import java.time.Instant

import org.springframework.stereotype.Service

import eu.transittrack.avl.model.AvlFeedRepository
import eu.transittrack.gtfs.model.TripRepository
import eu.transittrack.gtfs.read.RevisionResolver
import eu.transittrack.predict.read.dto.HeadwayDto
import eu.transittrack.schedule.model.ScheduleTimeRepository
import eu.transittrack.schedule.model.StopPathRepository
import eu.transittrack.schedule.model.TripPatternRepository

/**
 * Route-level headway/wait-time summary at a stop, derived from the same still-forward-looking
 * predictions [PredictionReadService.stopPredictions] exposes, plus a scheduled-headway baseline
 * computed straight from `schedule_time`.
 */
@Service
class HeadwayReadService(
    private val avlFeeds: AvlFeedRepository,
    private val revisionResolver: RevisionResolver,
    private val tripPatternsRepo: TripPatternRepository,
    private val stopPaths: StopPathRepository,
    private val scheduleTimes: ScheduleTimeRepository,
    private val trips: TripRepository,
    private val read: PredictionReadService,
) {
    fun headway(
        feedCode: String,
        stopId: String,
        routeId: String,
        directionId: Int?,
    ): HeadwayDto {
        val arrivals =
            read
                .stopPredictions(feedCode, stopId, routeId, directionId)
                .mapNotNull { it.predictedArrival?.let(Instant::parse) }
                .sorted()
        val waitSec =
            arrivals.firstOrNull()?.let {
                Duration
                    .between(Instant.now(), it)
                    .seconds
                    .toInt()
                    .coerceAtLeast(0)
            }
        val gaps = arrivals.zipWithNext { a, b -> Duration.between(a, b).seconds.toInt() }
        val scheduledHeadwaySec = computeScheduledHeadway(feedCode, stopId, routeId, directionId)
        return HeadwayDto(stopId, routeId, directionId, waitSec, gaps, scheduledHeadwaySec)
    }

    private fun computeScheduledHeadway(
        feedCode: String,
        stopId: String,
        routeId: String,
        directionId: Int?,
    ): Int? {
        val feed = avlFeeds.findByCode(feedCode) ?: throw IllegalArgumentException("no avl feed '$feedCode'")
        val revisionId = revisionResolver.resolve(feed.gtfsFeedCode, null)
        val patternIds =
            tripPatternsRepo
                .findByRouteId(revisionId, routeId)
                .filter { directionId == null || it.directionId == directionId }
                .map { it.id!! }
                .toSet()
        val matchingStopPaths = stopPaths.findByStopId(revisionId, stopId).filter { it.tripPatternId in patternIds }
        val scheduledArrivals =
            matchingStopPaths
                .flatMap { sp ->
                    trips.findByTripPattern(revisionId, sp.tripPatternId).mapNotNull { trip ->
                        scheduleTimes
                            .findByTripOrdered(revisionId, trip.id!!)
                            .firstOrNull { it.stopPathIndex == sp.stopPathIndex }
                            ?.arrivalSec
                    }
                }.sorted()
        if (scheduledArrivals.size < 2) return null
        val gaps = scheduledArrivals.zipWithNext { a, b -> b - a }
        return gaps.average().let { kotlin.math.round(it).toInt() }
    }
}
