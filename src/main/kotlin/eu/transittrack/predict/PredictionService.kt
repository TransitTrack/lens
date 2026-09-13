package eu.transittrack.predict

import java.time.Duration
import java.time.Instant
import kotlin.math.abs

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

import eu.transittrack.avl.match.AvlMatchContext
import eu.transittrack.avl.match.MatchOutcome
import eu.transittrack.avl.match.inferServiceDate
import eu.transittrack.avl.model.AvlFeed
import eu.transittrack.avl.model.AvlReportRow
import eu.transittrack.avl.model.VehicleState
import eu.transittrack.observability.TransitTrackMetrics
import eu.transittrack.predict.generate.PredictionStrategy
import eu.transittrack.predict.generate.buildHorizon
import eu.transittrack.predict.learn.KalmanState
import eu.transittrack.predict.learn.RunningAverageState
import eu.transittrack.predict.learn.detectCrossings
import eu.transittrack.predict.learn.updateKalman
import eu.transittrack.predict.learn.updateRunningAverage
import eu.transittrack.predict.model.KalmanTravelTimeStateRepository
import eu.transittrack.predict.model.PredictionAccuracy
import eu.transittrack.predict.model.TravelTimeObservationRepository
import eu.transittrack.predict.model.VehiclePredictionRepository
import eu.transittrack.schedule.model.TravelTimesForStopPathRepository

/**
 * Orchestration hub wiring prediction generation into AVL matching: on every matched report it
 * (1) detects stop-path crossings since the vehicle's previous state, learning travel times and
 * filling in the `actual_*`/`prediction_accuracy` rows for predictions already made for those
 * stops, and (2) generates fresh predictions for the vehicle's remaining horizon.
 */
@Component
@ConditionalOnProperty("transittrack.predict.enabled", havingValue = "true")
class PredictionService(
    private val observations: TravelTimeObservationRepository,
    private val kalmanStates: KalmanTravelTimeStateRepository,
    private val predictions: VehiclePredictionRepository,
    private val travelTimesForStopPath: TravelTimesForStopPathRepository,
    private val writer: PredictionWriter,
    strategyList: List<PredictionStrategy>,
    private val props: PredictProperties,
    private val metrics: TransitTrackMetrics,
) {
    private val strategies: Map<PredictionAlgorithm, PredictionStrategy> = strategyList.associateBy { it.algorithm }

    fun onMatched(
        feed: AvlFeed,
        report: AvlReportRow,
        prev: VehicleState?,
        outcome: MatchOutcome.Matched,
        ctx: AvlMatchContext,
    ) {
        val trip = ctx.trip(outcome.tripRowId) ?: return
        val serviceDate = inferServiceDate(report, trip, ctx.zone)
        val now = Instant.now()

        if (prev != null && prev.tripRowId == outcome.tripRowId && prev.stopPathIndex != null) {
            processCrossings(feed, report, prev, outcome, ctx, now)
        }

        val horizon = buildHorizon(outcome.tripRowId, outcome.tripPatternId, outcome.stopPathIndex, ctx)
        val algorithmsToRun =
            if (feed.predictionMode == PredictionMode.EVALUATION) strategies.values else listOfNotNull(strategies[feed.predictionAlgorithm])
        for (strategy in algorithmsToRun) {
            val startedAt = Instant.now()
            runCatching {
                val generated = strategy.predict(horizon, serviceDate, outcome.scheduleAdherenceSec ?: 0, report.ts, ctx)
                for (p in generated) {
                    writer.upsertPrediction(
                        VehiclePredictionUpsert(
                            feedId = feed.id!!,
                            vehicleId = report.vehicleId,
                            tripRowId = p.tripRowId,
                            blockPk = outcome.blockPk,
                            tripPatternId = p.tripPatternId,
                            stopPathIndex = p.stopPathIndex,
                            algorithm = strategy.algorithm,
                            predictedArrivalTs = p.predictedArrivalTs,
                            predictedDepartureTs = p.predictedDepartureTs,
                            actualArrivalTs = null,
                            actualDepartureTs = null,
                            confidenceSec = p.confidenceSec,
                            computedAt = now,
                        ),
                    )
                }
                generated.size
            }.onSuccess { generated ->
                metrics.predictionRun(
                    feed,
                    strategy.algorithm,
                    TransitTrackMetrics.Outcome.SUCCESS,
                    Duration.between(startedAt, Instant.now()),
                    generated,
                )
            }.onFailure {
                metrics.predictionRun(
                    feed,
                    strategy.algorithm,
                    TransitTrackMetrics.Outcome.FAILED,
                    Duration.between(startedAt, Instant.now()),
                    0,
                )
            }.getOrThrow()
        }
    }

    private fun processCrossings(
        feed: AvlFeed,
        report: AvlReportRow,
        prev: VehicleState,
        outcome: MatchOutcome.Matched,
        ctx: AvlMatchContext,
        now: Instant,
    ) {
        val geom = ctx.patternGeometry(outcome.tripPatternId) ?: return
        val elapsedSec = Duration.between(prev.reportTs, report.ts).seconds.toDouble()
        val crossings =
            detectCrossings(
                prev.stopPathIndex!!,
                outcome.stopPathIndex,
                elapsedSec,
                geom.stopPathCumM,
                geom.line.lengthM,
                props.learn.maxPlausibleTravelTimeSec,
            )
        metrics.predictionCrossings(feed, crossings.size)
        var cursor = prev.reportTs
        val algorithmsToScore =
            if (feed.predictionMode == PredictionMode.EVALUATION) PredictionAlgorithm.entries else listOf(feed.predictionAlgorithm)
        for (crossing in crossings) {
            cursor = cursor.plusSeconds(crossing.observedTravelTimeSec.toLong())
            learn(outcome.tripPatternId, crossing.stopPathIndex, crossing.observedTravelTimeSec, ctx, now)
            metrics.predictionLearningSamples(feed, 1)
            fillActualAndScoreAccuracy(feed, report.vehicleId, outcome.tripRowId, crossing.stopPathIndex, cursor, now, algorithmsToScore)
        }
    }

    private fun learn(
        tripPatternId: Long,
        stopPathIndex: Int,
        sample: Double,
        ctx: AvlMatchContext,
        now: Instant,
    ) {
        val seed =
            travelTimesForStopPath
                .findByTripPatternOrdered(ctx.revisionId, tripPatternId)
                .firstOrNull { it.stopPathIndex == stopPathIndex }
                ?.travelTimeSec
                ?.toDouble() ?: sample

        val prevObs = observations.findByTripPatternIdAndStopPathIndex(tripPatternId, stopPathIndex)
        val newObs = updateRunningAverage(prevObs?.let { RunningAverageState(it.sampleCount, it.meanSec) }, seed, sample)
        writer.upsertObservation(tripPatternId, stopPathIndex, newObs.sampleCount, newObs.meanSec, now)

        val prevKalman = kalmanStates.findByTripPatternIdAndStopPathIndex(tripPatternId, stopPathIndex)
        val newKalman =
            updateKalman(
                prevKalman?.let { KalmanState(it.estimateSec, it.errorVariance, it.sampleCount) },
                seed,
                props.learn.kalmanInitialVarianceSec2,
                props.learn.kalmanMeasurementNoiseSec2,
                sample,
            )
        writer.upsertKalmanState(tripPatternId, stopPathIndex, newKalman.estimateSec, newKalman.errorVariance, newKalman.sampleCount, now)
    }

    private fun fillActualAndScoreAccuracy(
        feed: AvlFeed,
        vehicleId: String,
        tripRowId: Long,
        stopPathIndex: Int,
        actualTs: Instant,
        now: Instant,
        algorithms: Collection<PredictionAlgorithm>,
    ) {
        val existing = predictions.findByFeedIdAndVehicleIdAndTripRowIdOrderByStopPathIndex(feed.id!!, vehicleId, tripRowId)
        for (algorithm in algorithms) {
            val row = existing.firstOrNull { it.stopPathIndex == stopPathIndex && it.algorithm == algorithm } ?: continue
            val predictedTs = row.predictedArrivalTs ?: continue
            writer.upsertPrediction(
                VehiclePredictionUpsert(
                    feedId = row.feedId,
                    vehicleId = row.vehicleId,
                    tripRowId = row.tripRowId,
                    blockPk = row.blockPk,
                    tripPatternId = row.tripPatternId,
                    stopPathIndex = row.stopPathIndex,
                    algorithm = row.algorithm,
                    predictedArrivalTs = row.predictedArrivalTs,
                    predictedDepartureTs = row.predictedDepartureTs,
                    actualArrivalTs = actualTs,
                    actualDepartureTs = row.actualDepartureTs,
                    confidenceSec = row.confidenceSec,
                    computedAt = row.computedAt,
                ),
            )
            val errorSec = Duration.between(predictedTs, actualTs).seconds.toInt()
            writer.insertAccuracy(
                PredictionAccuracy(
                    feedId = feed.id!!,
                    vehicleId = vehicleId,
                    tripRowId = tripRowId,
                    stopPathIndex = stopPathIndex,
                    algorithm = algorithm,
                    predictedTs = predictedTs,
                    actualTs = actualTs,
                    errorSec = errorSec,
                    absErrorSec = abs(errorSec),
                    createdAt = now,
                ),
            )
            metrics.predictionAccuracy(feed, algorithm, errorSec)
        }
    }
}
