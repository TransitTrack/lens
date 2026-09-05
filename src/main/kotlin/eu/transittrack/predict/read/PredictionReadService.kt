package eu.transittrack.predict.read

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

import org.springframework.stereotype.Service

import eu.transittrack.avl.model.AvlFeedRepository
import eu.transittrack.avl.model.VehicleStateRepository
import eu.transittrack.predict.PredictionAlgorithm
import eu.transittrack.predict.generate.serviceSecToInstant
import eu.transittrack.predict.model.PredictionAccuracyRepository
import eu.transittrack.predict.model.VehiclePredictionRepository
import eu.transittrack.predict.read.dto.PredictionAccuracySummaryDto
import eu.transittrack.predict.read.dto.StopPredictionDto
import eu.transittrack.schedule.model.ScheduleTimeRepository
import eu.transittrack.schedule.model.StopPathRepository

@Service
class PredictionReadService(
    private val avlFeeds: AvlFeedRepository,
    private val vehicleStates: VehicleStateRepository,
    private val predictions: VehiclePredictionRepository,
    private val accuracy: PredictionAccuracyRepository,
    private val scheduleTimes: ScheduleTimeRepository,
    private val stopPaths: StopPathRepository,
) {
    fun vehiclePredictions(
        feedCode: String,
        vehicleId: String,
    ): List<StopPredictionDto> {
        val feed = avlFeeds.findByCode(feedCode) ?: throw IllegalArgumentException("no avl feed '$feedCode'")
        val state = vehicleStates.findByFeedIdAndVehicleId(feed.id!!, vehicleId) ?: return emptyList()
        val tripRowId = state.tripRowId ?: return emptyList()
        val revisionId = state.revisionId ?: return emptyList()
        val tripPatternId = state.tripPatternId ?: return emptyList()

        val rows = predictions.findByFeedIdAndVehicleIdAndTripRowIdOrderByStopPathIndex(feed.id!!, vehicleId, tripRowId)
        val schedule = scheduleTimes.findByTripOrdered(revisionId, tripRowId).associateBy { it.stopPathIndex }
        val stopPathsByIndex = stopPaths.findByTripPatternOrdered(revisionId, tripPatternId).associateBy { it.stopPathIndex }
        val zone = ZoneId.systemDefault()

        return rows.mapNotNull { r ->
            val stopId = stopPathsByIndex[r.stopPathIndex]?.stopId ?: return@mapNotNull null
            val sched = schedule[r.stopPathIndex]
            // Best-effort: derive the display service date from this row's own computedAt, since the
            // read layer doesn't otherwise carry the original AVL match's service date. Adequate for
            // a display convenience field; a trip crossing midnight around computedAt could be off by
            // a day — acceptable, this is not used for any matching/accuracy logic, only display.
            val serviceDate = r.computedAt.atZone(zone).toLocalDate()
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

    fun predictionAccuracy(
        feedCode: String,
        algorithm: PredictionAlgorithm?,
        sinceDays: Int,
    ): List<PredictionAccuracySummaryDto> {
        val feed = avlFeeds.findByCode(feedCode) ?: throw IllegalArgumentException("no avl feed '$feedCode'")
        val since = Instant.now().minus(sinceDays.toLong(), ChronoUnit.DAYS)
        val algorithms = algorithm?.let { listOf(it) } ?: PredictionAlgorithm.entries.toList()
        return algorithms.mapNotNull { alg ->
            val rows = accuracy.findByFeedIdAndAlgorithmAndCreatedAtAfter(feed.id!!, alg, since)
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
