package eu.transittrack.predict

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlin.math.abs

import org.hibernate.StatelessSession
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

import eu.transittrack.avl.match.AvlMatchContext
import eu.transittrack.avl.match.AvlReportView
import eu.transittrack.avl.match.MatchOutcome
import eu.transittrack.avl.match.cache.CachedLearningKeyReader
import eu.transittrack.avl.match.inferServiceDate
import eu.transittrack.avl.model.AvlFeed
import eu.transittrack.avl.model.AvlReportRow
import eu.transittrack.predict.generate.PredictionStrategy
import eu.transittrack.predict.generate.buildHorizon
import eu.transittrack.predict.generate.seedStopToStopSec
import eu.transittrack.predict.learn.KalmanState
import eu.transittrack.predict.learn.LearningKey
import eu.transittrack.predict.learn.RunningAverageState
import eu.transittrack.predict.learn.TravelTimeBucket
import eu.transittrack.predict.learn.detectStopPasses
import eu.transittrack.predict.learn.updateKalman
import eu.transittrack.predict.learn.updateRunningAverage
import eu.transittrack.predict.model.AvlStopCrossing
import eu.transittrack.predict.model.AvlStopCrossingRepository
import eu.transittrack.predict.model.KalmanTravelTimeStateRepository
import eu.transittrack.predict.model.PredictionAccuracy
import eu.transittrack.predict.model.TravelTimeObservationRepository
import eu.transittrack.predict.model.VehiclePrediction
import eu.transittrack.predict.model.VehiclePredictionRepository
import eu.transittrack.schedule.model.TravelTimesForStopPathRepository
import eu.transittrack.schedule.model.TripRef
import eu.transittrack.util.observability.TransitTrackMetrics

/** The vehicle's previous match, as needed for stop-pass detection — deliberately not a full
 * [eu.transittrack.avl.model.VehicleState], since [PredictionService] is reconstructed from
 * persisted `vehicle_match` history, not a live mutable state row. */
data class PrevVehicleMatch(
    val tripRowId: Long,
    val stopPathIndex: Int,
    val distanceAlongTripM: Double,
    val reportTs: Instant,
)

/**
 * Orchestration hub wiring prediction generation into AVL matching: on every matched report it
 * (1) detects the stops the vehicle passed since its previous match on the same trip, recording
 * each pass, filling in the `actual_*`/`prediction_accuracy` rows for predictions already made for
 * those stops, and learning stop-to-stop times; and (2) generates fresh predictions for the
 * vehicle's remaining horizon, dropping its open predictions that fell out of it.
 */
@Component
@ConditionalOnProperty("transittrack.predict.enabled", havingValue = "true")
class PredictionService(
    private val observations: TravelTimeObservationRepository,
    private val kalmanStates: KalmanTravelTimeStateRepository,
    private val predictions: VehiclePredictionRepository,
    private val crossings: AvlStopCrossingRepository,
    private val travelTimesForStopPath: TravelTimesForStopPathRepository,
    private val learningKeys: CachedLearningKeyReader,
    private val writer: PredictionWriter,
    strategyList: List<PredictionStrategy>,
    private val props: PredictProperties,
    private val metrics: TransitTrackMetrics,
) {
    private val strategies: Map<PredictionAlgorithm, PredictionStrategy> = strategyList.associateBy { it.algorithm }

    /**
     * [generatePredictions] is false for a vehicle's older matches in a backlog: their stop passes
     * still have to be learned, in order, but only the newest match's horizon is worth predicting.
     */
    fun onMatched(
        feed: AvlFeed,
        report: AvlReportRow,
        prev: PrevVehicleMatch?,
        outcome: MatchOutcome.Matched,
        ctx: AvlMatchContext,
        generatePredictions: Boolean = true,
    ) {
        val trip = ctx.trip(outcome.tripRowId) ?: return
        val serviceDate = inferServiceDate(report.toView(), trip, ctx.zone)
        val now = Instant.now()

        // Everything below writes through the same StatelessSession/transaction: a matched report
        // can fan out into dozens of individual upserts (per horizon stop, per algorithm, plus
        // passes/learning), and committing each one separately made that commit's fsync the
        // dominant cost of the whole prediction pipeline.
        writer.inTransaction { session ->
            if (prev != null && prev.tripRowId == outcome.tripRowId) {
                processStopPasses(session, feed, report, prev, outcome, trip, serviceDate, ctx, now)
            }
            if (!generatePredictions) return@inTransaction

            val horizon =
                buildHorizon(outcome.tripRowId, outcome.tripPatternId, outcome.stopPathIndex, outcome.distanceAlongTripM, ctx)
            writer.deleteOutOfHorizonPredictions(
                session,
                feed.code,
                report.vehicleId,
                outcome.tripRowId,
                horizon.firstOrNull { it.tripRowId == outcome.tripRowId }?.stopPathIndex ?: Int.MAX_VALUE,
                horizon.map { it.tripRowId }.toSet() + outcome.tripRowId,
            )

            val algorithmsToRun = feed.predictionAlgorithms.mapNotNull { strategies[it] }
            for (strategy in algorithmsToRun) {
                val startedAt = Instant.now()
                runCatching {
                    val generated = strategy.predict(horizon, serviceDate, outcome.scheduleAdherenceSec, report.ts, ctx)
                    for (p in generated) {
                        writer.upsertPrediction(
                            session,
                            VehiclePredictionUpsert(
                                feedCode = feed.code,
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
                        feed.code,
                        strategy.algorithm.name.lowercase(),
                        TransitTrackMetrics.Outcome.SUCCESS,
                        Duration.between(startedAt, Instant.now()),
                        generated,
                    )
                }.onFailure {
                    metrics.predictionRun(
                        feed.code,
                        strategy.algorithm.name.lowercase(),
                        TransitTrackMetrics.Outcome.FAILED,
                        Duration.between(startedAt, Instant.now()),
                        0,
                    )
                }.getOrThrow()
            }
        }
    }

    /**
     * Records every stop passed between [prev] and this match. A pass whose previous stop on the
     * same trip was also seen yields one stop-to-stop sample (time from passing the previous stop
     * to reaching this one), which is what the learners and the optimizer consume; the first stop
     * counts as passed when the vehicle leaves it.
     */
    private fun processStopPasses(
        session: StatelessSession,
        feed: AvlFeed,
        report: AvlReportRow,
        prev: PrevVehicleMatch,
        outcome: MatchOutcome.Matched,
        trip: TripRef,
        serviceDate: LocalDate,
        ctx: AvlMatchContext,
        now: Instant,
    ) {
        val geom = ctx.patternGeometry(outcome.tripPatternId) ?: return
        val learningKey = learningKeys.keyOf(ctx.revisionId, outcome.tripPatternId)
        val passes =
            detectStopPasses(
                prev.distanceAlongTripM,
                prev.reportTs,
                outcome.distanceAlongTripM,
                report.ts,
                geom.stopPathCumM,
                geom.line.lengthM,
            )
        if (passes.isEmpty()) return

        val maxPlausibleSec = props.learn.maxPlausibleTravelTimeSec
        // Same trip row on an earlier service day, or a stale pass, is never this trip's previous stop.
        val since = report.ts.minusSeconds(maxPlausibleSec.toLong())
        val seeds = travelTimesForStopPath.findByTripPatternOrdered(ctx.revisionId, outcome.tripPatternId).associateBy { it.stopPathIndex }
        val existingPredictions = predictions.findByVehicleTrip(feed.code, report.vehicleId, outcome.tripRowId)
        val passedHere = HashMap<Int, Instant>()
        var recorded = 0

        for (pass in passes) {
            val k = pass.stopPathIndex
            // GPS jitter can carry a vehicle across the same stop twice; only the first pass counts.
            if (crossings.findLastPass(feed.code, report.vehicleId, outcome.tripRowId, k, since) != null) continue

            val previousPassAt =
                if (k == 0) {
                    null
                } else {
                    passedHere[k - 1] ?: crossings.findLastPass(feed.code, report.vehicleId, outcome.tripRowId, k - 1, since)?.observedAt
                }
            val sampleSec =
                previousPassAt
                    ?.let { Duration.between(it, pass.passedAt).toMillis() / 1000.0 }
                    ?.takeIf { it > 0.0 && it <= maxPlausibleSec }
            if (sampleSec != null && previousPassAt != null && learningKey != null) {
                // Bucketed by when the vehicle left the previous stop, the same moment predictions
                // look a stop path's time up by.
                val bucket = TravelTimeBucket.at(serviceDate, ctx.zone, previousPassAt)
                learn(session, learningKey, k, bucket, sampleSec, seedStopToStopSec(seeds, k) ?: sampleSec, now)
                metrics.predictionLearningSamples(feed.code, 1)
            }

            writer.insertCrossing(
                session,
                AvlStopCrossing(
                    feedCode = feed.code,
                    vehicleId = report.vehicleId,
                    tripRowId = outcome.tripRowId,
                    tripPatternId = outcome.tripPatternId,
                    stopPathIndex = k,
                    observedAt = pass.passedAt,
                    observedTravelTimeSec = sampleSec,
                    serviceId = trip.serviceId,
                    tripStartSec = trip.startTimeSec,
                    routeId = trip.routeId,
                    directionId = trip.directionId,
                    revisionId = ctx.revisionId,
                ),
            )
            fillActualAndScoreAccuracy(session, feed, existingPredictions, k, pass.passedAt, departure = k == 0, now)
            passedHere[k] = pass.passedAt
            recorded++
        }
        metrics.predictionCrossings(feed.code, recorded)
    }

    /** Updates both learners for [bucket] and its coarser roll-ups (its day type, and overall). */
    private fun learn(
        session: StatelessSession,
        key: LearningKey,
        stopPathIndex: Int,
        bucket: TravelTimeBucket,
        sample: Double,
        seed: Double,
        now: Instant,
    ) {
        for (b in bucket.withRollups()) {
            val prevObs = observations.findInBucket(key.gtfsFeedCode, key.patternKey, stopPathIndex, b.dayType, b.timeBand)
            val newObs =
                updateRunningAverage(
                    prevObs?.let { RunningAverageState(it.sampleCount, it.meanSec) },
                    seed,
                    sample,
                    props.learn.historyWindow,
                )
            writer.upsertObservation(session, key, stopPathIndex, b, newObs.sampleCount, newObs.meanSec, now)

            val prevKalman = kalmanStates.findInBucket(key.gtfsFeedCode, key.patternKey, stopPathIndex, b.dayType, b.timeBand)
            val newKalman =
                updateKalman(
                    prevKalman?.let { KalmanState(it.estimateSec, it.errorVariance, it.sampleCount) },
                    seed,
                    props.learn.kalmanInitialVarianceSec2,
                    props.learn.kalmanMeasurementNoiseSec2,
                    props.learn.kalmanProcessNoiseSec2,
                    sample,
                )
            writer.upsertKalmanState(
                session,
                key,
                stopPathIndex,
                b,
                newKalman.estimateSec,
                newKalman.errorVariance,
                newKalman.sampleCount,
                now,
            )
        }
    }

    /** [departure] is set for the first stop, whose "pass" is the vehicle leaving it. */
    private fun fillActualAndScoreAccuracy(
        session: StatelessSession,
        feed: AvlFeed,
        existing: List<VehiclePrediction>,
        stopPathIndex: Int,
        actualTs: Instant,
        departure: Boolean,
        now: Instant,
    ) {
        for (algorithm in feed.predictionAlgorithms) {
            val row = existing.firstOrNull { it.stopPathIndex == stopPathIndex && it.algorithm == algorithm } ?: continue
            val predictedTs = (if (departure) row.predictedDepartureTs else row.predictedArrivalTs) ?: continue
            writer.upsertPrediction(
                session,
                VehiclePredictionUpsert(
                    feedCode = row.feedCode,
                    vehicleId = row.vehicleId,
                    tripRowId = row.tripRowId,
                    blockPk = row.blockPk,
                    tripPatternId = row.tripPatternId,
                    stopPathIndex = row.stopPathIndex,
                    algorithm = row.algorithm,
                    predictedArrivalTs = row.predictedArrivalTs,
                    predictedDepartureTs = row.predictedDepartureTs,
                    actualArrivalTs = if (departure) row.actualArrivalTs else actualTs,
                    actualDepartureTs = if (departure) actualTs else row.actualDepartureTs,
                    confidenceSec = row.confidenceSec,
                    computedAt = row.computedAt,
                ),
            )
            val errorSec = Duration.between(predictedTs, actualTs).seconds.toInt()
            writer.insertAccuracy(
                session,
                PredictionAccuracy(
                    feedCode = feed.code,
                    vehicleId = row.vehicleId,
                    tripRowId = row.tripRowId,
                    stopPathIndex = stopPathIndex,
                    algorithm = algorithm,
                    predictedTs = predictedTs,
                    actualTs = actualTs,
                    errorSec = errorSec,
                    absErrorSec = abs(errorSec),
                    createdAt = now,
                ),
            )
            metrics.predictionAccuracy(feed.code, algorithm.name.lowercase(), errorSec)
        }
    }
}

private fun AvlReportRow.toView() =
    AvlReportView(
        id = id!!,
        feedCode = feedCode,
        vehicleId = vehicleId,
        vehicleLabel = vehicleLabel,
        ts = ts,
        lat = lat,
        lon = lon,
        bearing = bearing,
        speedMps = speedMps,
        odometerM = odometerM,
        descTripId = descTripId,
        descRouteId = descRouteId,
        descDirectionId = descDirectionId,
        descStartDate = descStartDate,
        descStartTimeSec = descStartTimeSec,
        descScheduleRelationship = descScheduleRelationship,
        currentStopSequence = currentStopSequence,
        currentStopId = currentStopId,
        currentStatus = currentStatus,
        occupancyStatus = occupancyStatus,
        congestionLevel = congestionLevel,
    )
