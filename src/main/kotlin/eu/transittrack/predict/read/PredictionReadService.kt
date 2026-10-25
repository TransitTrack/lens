package eu.transittrack.predict.read

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

import org.springframework.stereotype.Service

import eu.transittrack.avl.match.cache.CachedAgencyReader
import eu.transittrack.avl.model.AvlFeedRepository
import eu.transittrack.avl.model.VehicleStateRepository
import eu.transittrack.gtfs.read.RevisionResolver
import eu.transittrack.predict.PredictionAlgorithm
import eu.transittrack.predict.generate.serviceSecToInstant
import eu.transittrack.predict.model.PredictionAccuracyRepository
import eu.transittrack.predict.model.VehiclePredictionRepository
import eu.transittrack.predict.read.dto.PredictionAccuracySummaryDto
import eu.transittrack.predict.read.dto.StopPredictionDto
import eu.transittrack.schedule.model.ScheduleTimeRepository
import eu.transittrack.schedule.model.StopPathRepository
import eu.transittrack.schedule.model.TripPatternRepository

@Service
class PredictionReadService(
    private val avlFeeds: AvlFeedRepository,
    private val vehicleStates: VehicleStateRepository,
    private val predictions: VehiclePredictionRepository,
    private val accuracy: PredictionAccuracyRepository,
    private val scheduleTimes: ScheduleTimeRepository,
    private val stopPaths: StopPathRepository,
    private val revisionResolver: RevisionResolver,
    private val tripPatternsRepo: TripPatternRepository,
    private val agencies: CachedAgencyReader,
) {
    fun vehiclePredictions(
        feedCode: String,
        vehicleId: String,
        algorithm: PredictionAlgorithm? = null,
    ): List<StopPredictionDto> {
        val feed = avlFeeds.findByCode(feedCode) ?: throw IllegalArgumentException("no avl feed '$feedCode'")
        val state = vehicleStates.findByFeedCodeAndVehicleId(feed.code, vehicleId) ?: return emptyList()
        val tripRowId = state.tripRowId ?: return emptyList()
        val revisionId = state.revisionId ?: return emptyList()
        val tripPatternId = state.tripPatternId ?: return emptyList()

        val rows = predictions.findByVehicleTrip(feed.code, vehicleId, tripRowId, algorithm)
        val scheduleRows = scheduleTimes.findByTripOrdered(revisionId, tripRowId)
        val schedule = scheduleRows.associateBy { it.stopPathIndex }
        val tripStartSec = scheduleRows.firstOrNull()?.let { it.departureSec ?: it.arrivalSec }
        val stopPathsByIndex = stopPaths.findByTripPatternOrdered(revisionId, tripPatternId).associateBy { it.stopPathIndex }
        val zone = agencies.timezoneOf(revisionId) ?: ZoneId.systemDefault()

        return rows.mapNotNull { r ->
            val stopId = stopPathsByIndex[r.stopPathIndex]?.stopId ?: return@mapNotNull null
            val sched = schedule[r.stopPathIndex]
            val serviceDate = displayServiceDate(r.computedAt, zone, tripStartSec)
            StopPredictionDto(
                stopPathIndex = r.stopPathIndex,
                scheduledArrival = sched?.arrivalSec?.let { serviceSecToInstant(serviceDate, it, zone).toString() },
                scheduledDeparture = sched?.departureSec?.let { serviceSecToInstant(serviceDate, it, zone).toString() },
                actualArrival = r.actualArrivalTs?.toString(),
                actualDeparture = r.actualDepartureTs?.toString(),
                predictedArrival = r.predictedArrivalTs?.toString(),
                predictedDeparture = r.predictedDepartureTs?.toString(),
                algorithm = r.algorithm.name,
                confidenceSec = r.confidenceSec,
                revisionId = revisionId,
                gtfsFeedCode = feed.gtfsFeedCode,
                stopId = stopId,
            )
        }
    }

    fun stopPredictions(
        feedCode: String,
        stopId: String,
        routeId: String?,
        directionId: Int?,
        algorithm: PredictionAlgorithm? = null,
    ): List<StopPredictionDto> {
        val feed = avlFeeds.findByCode(feedCode) ?: throw IllegalArgumentException("no avl feed '$feedCode'")
        val revisionId = revisionResolver.resolve(feed.gtfsFeedCode, null)
        val patternIds = tripPatterns(revisionId, routeId, directionId).map { it.id!! }.toSet()
        val matchingStopPaths = stopPaths.findByStopId(revisionId, stopId).filter { it.tripPatternId in patternIds }
        val zone = agencies.timezoneOf(revisionId) ?: ZoneId.systemDefault()
        val resolvedAlgorithm = algorithm ?: feed.predictionAlgorithms.first()
        val now = Instant.now()
        return matchingStopPaths.flatMap { sp ->
            predictions
                .findByPatternStopAlgorithm(sp.tripPatternId, sp.stopPathIndex, resolvedAlgorithm)
                .filter { it.predictedArrivalTs != null && it.actualArrivalTs == null && isLive(it.computedAt, now) }
                .map { r ->
                    val scheduleRows = scheduleTimes.findByTripOrdered(revisionId, r.tripRowId)
                    val sched = scheduleRows.firstOrNull { it.stopPathIndex == r.stopPathIndex }
                    val tripStartSec = scheduleRows.firstOrNull()?.let { it.departureSec ?: it.arrivalSec }
                    val serviceDate = displayServiceDate(r.computedAt, zone, tripStartSec)
                    StopPredictionDto(
                        stopPathIndex = r.stopPathIndex,
                        scheduledArrival = sched?.arrivalSec?.let { serviceSecToInstant(serviceDate, it, zone).toString() },
                        scheduledDeparture = sched?.departureSec?.let { serviceSecToInstant(serviceDate, it, zone).toString() },
                        actualArrival = null,
                        actualDeparture = null,
                        predictedArrival = r.predictedArrivalTs?.toString(),
                        predictedDeparture = r.predictedDepartureTs?.toString(),
                        algorithm = r.algorithm.name,
                        confidenceSec = r.confidenceSec,
                        revisionId = revisionId,
                        gtfsFeedCode = feed.gtfsFeedCode,
                        stopId = stopId,
                    )
                }
        }
    }

    /**
     * An open prediction only belongs on a stop board while its vehicle is still reporting, i.e. it
     * was refreshed recently. Every refresh already drops the vehicle's open predictions behind it
     * (`PredictionWriter.deleteOutOfHorizonPredictions`); what's left stale is a vehicle that went
     * silent or unmatched, whose last predictions would otherwise sit there as "arriving" for hours.
     */
    private fun isLive(
        computedAt: Instant,
        now: Instant,
    ): Boolean = computedAt.isAfter(now.minus(REFRESH_MAX_AGE))

    /**
     * Service date a prediction belongs to, from when it was computed - with the same rule matching
     * uses (`inferServiceDate`): a trip starting late in the evening, seen after midnight, still
     * belongs to the previous service day.
     */
    private fun displayServiceDate(
        computedAt: Instant,
        zone: ZoneId,
        tripStartSec: Int?,
    ): LocalDate {
        val local = computedAt.atZone(zone)
        val secOfDay = local.toLocalTime().toSecondOfDay()
        return if (tripStartSec != null && secOfDay + 14_400 < tripStartSec) local.toLocalDate().minusDays(1) else local.toLocalDate()
    }

    private companion object {
        val REFRESH_MAX_AGE: Duration = Duration.ofMinutes(10)
    }

    private fun tripPatterns(
        revisionId: Long,
        routeId: String?,
        directionId: Int?,
    ): List<eu.transittrack.schedule.model.TripPattern> {
        // stopPredictions without a routeId has nothing to scope the pattern search to; return empty
        // rather than scanning every pattern in the revision.
        if (routeId == null) return emptyList()
        return tripPatternsRepo.findByRouteId(revisionId, routeId).filter { directionId == null || it.directionId == directionId }
    }

    fun predictionAccuracy(
        feedCode: String,
        algorithm: PredictionAlgorithm?,
        sinceDays: Int,
    ): List<PredictionAccuracySummaryDto> {
        val feed = avlFeeds.findByCode(feedCode) ?: throw IllegalArgumentException("no avl feed '$feedCode'")
        val since = Instant.now().minus(sinceDays.toLong(), ChronoUnit.DAYS)
        val algorithms = algorithm?.let { listOf(it) } ?: PredictionAlgorithm.entries.toList()
        return algorithms.mapNotNull { alg ->
            val rows = accuracy.findRecentByFeedAndAlgorithm(feed.code, alg, since)
            if (rows.isEmpty()) return@mapNotNull null
            PredictionAccuracySummaryDto(
                algorithm = alg.name,
                sampleCount = rows.size,
                meanErrorSec = rows.map { it.errorSec }.average(),
                meanAbsErrorSec = rows.map { it.absErrorSec }.average(),
            )
        }
    }
}
