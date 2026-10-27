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
import eu.transittrack.predict.model.VehiclePrediction
import eu.transittrack.predict.model.VehiclePredictionRepository
import eu.transittrack.predict.read.dto.PredictionAccuracyHorizonDto
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
                algorithm = r.algorithm,
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
        val rows =
            matchingStopPaths
                .flatMap { sp -> predictions.findByPatternStopAlgorithm(feed.code, sp.tripPatternId, sp.stopPathIndex, resolvedAlgorithm) }
                .filter { it.predictedArrivalTs != null && it.actualArrivalTs == null && isLive(it.computedAt, now) }
                .let { onePerTrip(feed.code, it) }
        if (rows.isEmpty()) return emptyList()

        // Each trip's row at this stop, plus its first one for the trip's start time.
        val scheduleRows =
            scheduleTimes
                .findByTripsAtStopPaths(revisionId, rows.map { it.tripRowId }.toSet(), rows.map { it.stopPathIndex }.toSet() + 0)
                .groupBy { it.tripId }
        return rows.map { r ->
            val tripSchedule = scheduleRows[r.tripRowId].orEmpty()
            val sched = tripSchedule.firstOrNull { it.stopPathIndex == r.stopPathIndex }
            val tripStartSec = tripSchedule.firstOrNull { it.stopPathIndex == 0 }?.let { it.departureSec ?: it.arrivalSec }
            val serviceDate = displayServiceDate(r.computedAt, zone, tripStartSec)
            StopPredictionDto(
                stopPathIndex = r.stopPathIndex,
                scheduledArrival = sched?.arrivalSec?.let { serviceSecToInstant(serviceDate, it, zone).toString() },
                scheduledDeparture = sched?.departureSec?.let { serviceSecToInstant(serviceDate, it, zone).toString() },
                actualArrival = null,
                actualDeparture = null,
                predictedArrival = r.predictedArrivalTs?.toString(),
                predictedDeparture = r.predictedDepartureTs?.toString(),
                algorithm = r.algorithm,
                confidenceSec = r.confidenceSec,
                revisionId = revisionId,
                gtfsFeedCode = feed.gtfsFeedCode,
                stopId = stopId,
            )
        }
    }

    /**
     * One prediction per trip: a trip can be predicted by two vehicles at once - the one running it,
     * and one that has it as the next trip of its block (e.g. after a vehicle swap). The vehicle
     * actually on the trip wins; failing that, the most recently computed prediction.
     */
    private fun onePerTrip(
        feedCode: String,
        rows: List<VehiclePrediction>,
    ): List<VehiclePrediction> {
        val byTrip = rows.groupBy { it.tripRowId }
        if (byTrip.values.none { it.size > 1 }) return rows
        val currentTrip = HashMap<String, Long?>()
        return byTrip.values.map { candidates ->
            candidates.singleOrNull()
                ?: candidates.firstOrNull { r ->
                    currentTrip.getOrPut(r.vehicleId) { vehicleStates.findByFeedCodeAndVehicleId(feedCode, r.vehicleId)?.tripRowId } ==
                        r.tripRowId
                }
                ?: candidates.maxBy { it.computedAt }
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
        return accuracy
            .aggregateByHorizon(feed.code, algorithms, since)
            .groupBy { it.algorithm }
            .toSortedMap()
            .map { (alg, horizons) ->
                val samples = horizons.sumOf { it.sampleCount }
                PredictionAccuracySummaryDto(
                    algorithm = alg,
                    sampleCount = samples.toInt(),
                    meanErrorSec = horizons.sumOf { it.meanErrorSec * it.sampleCount } / samples,
                    meanAbsErrorSec = horizons.sumOf { it.meanAbsErrorSec * it.sampleCount } / samples,
                    byHorizon =
                        horizons.map {
                            PredictionAccuracyHorizonDto(it.horizonSec, it.sampleCount.toInt(), it.meanErrorSec, it.meanAbsErrorSec)
                        },
                )
            }
    }
}
