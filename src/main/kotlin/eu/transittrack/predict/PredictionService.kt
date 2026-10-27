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
import eu.transittrack.avl.match.cache.CachedTravelTimesReader
import eu.transittrack.avl.match.inferServiceDate
import eu.transittrack.avl.model.AvlFeed
import eu.transittrack.avl.model.AvlReportRow
import eu.transittrack.predict.generate.GeneratedPrediction
import eu.transittrack.predict.generate.HorizonStop
import eu.transittrack.predict.generate.PredictionStrategy
import eu.transittrack.predict.generate.buildHorizon
import eu.transittrack.predict.generate.seedStopToStopSec
import eu.transittrack.predict.learn.LearningKey
import eu.transittrack.predict.learn.SampleClip
import eu.transittrack.predict.learn.StopPass
import eu.transittrack.predict.learn.TravelTimeBucket
import eu.transittrack.predict.learn.detectStopPasses
import eu.transittrack.predict.learn.updateKalman
import eu.transittrack.predict.learn.updateRunningAverage
import eu.transittrack.predict.model.AvlStopCrossing
import eu.transittrack.predict.model.PredictionAccuracy
import eu.transittrack.schedule.model.TravelTimesForStopPath
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
 * each pass, filling in the `actual_*` of the predictions made for those stops and scoring their
 * accuracy checkpoints, and learning stop-to-stop times; and (2) generates fresh predictions for
 * the vehicle's remaining horizon, dropping its open predictions that fell out of it and keeping
 * accuracy checkpoints for the stops that just came within a lead-time horizon.
 *
 * Strategies run before the transaction opens, each isolated from the others: a failing strategy
 * neither holds the transaction (and the learner rows it locks) open nor rolls back the stop passes
 * and learning of the same report. Its failure is rethrown once everything else is committed, so
 * the match is still marked failed.
 */
@Component
@ConditionalOnProperty("transittrack.predict.enabled", havingValue = "true")
class PredictionService(
    private val travelTimes: CachedTravelTimesReader,
    private val learningKeys: CachedLearningKeyReader,
    private val writer: PredictionWriter,
    strategyList: List<PredictionStrategy>,
    private val props: PredictProperties,
    private val metrics: TransitTrackMetrics,
) {
    private val strategies: Map<PredictionAlgorithm, PredictionStrategy> = strategyList.associateBy { it.algorithm }
    private val horizonsSec: List<Int> = props.accuracy.horizonsSec
        .filter { it > 0 }
        .distinct()
        .sorted()
    private val clip = SampleClip(props.learn.outlierMinDeviationSec, props.learn.outlierMaxRelativeDeviation)

    /** Stop passes detected for one match, with everything learning from them needs. */
    private class DetectedPasses(
        val passes: List<StopPass>,
        val learningKey: LearningKey?,
        val seeds: Map<Int, TravelTimesForStopPath>,
    )

    /** [horizon] is null when it couldn't be built, which leaves the vehicle's predictions as they were. */
    private class Generation(
        val horizon: List<HorizonStop>?,
        val generated: List<Pair<PredictionAlgorithm, List<GeneratedPrediction>>>,
        val failure: Throwable?,
    )

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

        val detected = if (prev != null && prev.tripRowId == outcome.tripRowId) detectPasses(report, prev, outcome, ctx) else null
        val generation = if (generatePredictions) generate(feed, report, outcome, serviceDate, ctx) else null

        // Everything below writes through the same StatelessSession/transaction: a matched report
        // can fan out into hundreds of rows (per horizon stop, per algorithm, plus passes/learning),
        // and committing each one separately made that commit's fsync the dominant cost of the whole
        // prediction pipeline. Each set of rows goes out as one batch.
        writer.inTransaction { session ->
            if (detected != null) recordStopPasses(session, feed, report, outcome, trip, serviceDate, ctx, detected, now)
            if (generation != null) writePredictions(session, feed, report, outcome, generation, now)
        }
        generation?.failure?.let { throw it }
    }

    private fun detectPasses(
        report: AvlReportRow,
        prev: PrevVehicleMatch,
        outcome: MatchOutcome.Matched,
        ctx: AvlMatchContext,
    ): DetectedPasses? {
        val geom = ctx.patternGeometry(outcome.tripPatternId) ?: return null
        val passes =
            detectStopPasses(
                prev.distanceAlongTripM,
                prev.reportTs,
                outcome.distanceAlongTripM,
                report.ts,
                geom.stopPathCumM,
                geom.line.lengthM,
            )
        if (passes.isEmpty()) return null
        return DetectedPasses(
            passes,
            learningKeys.keyOf(ctx.revisionId, outcome.tripPatternId),
            travelTimes.byStopPath(ctx.revisionId, outcome.tripPatternId),
        )
    }

    /** Runs every algorithm configured for [feed], each on its own: one failing doesn't drop the others' output. */
    private fun generate(
        feed: AvlFeed,
        report: AvlReportRow,
        outcome: MatchOutcome.Matched,
        serviceDate: LocalDate,
        ctx: AvlMatchContext,
    ): Generation {
        val horizon =
            runCatching {
                buildHorizon(outcome.tripRowId, outcome.tripPatternId, outcome.stopPathIndex, outcome.distanceAlongTripM, ctx)
            }.getOrElse { return Generation(null, emptyList(), it) }

        var failure: Throwable? = null
        val generated =
            feed.predictionAlgorithms.mapNotNull { strategies[it] }.mapNotNull { strategy ->
                val startedAt = Instant.now()
                val algorithmTag = strategy.algorithm.name.lowercase()
                runCatching { strategy.predict(horizon, serviceDate, outcome.scheduleAdherenceSec, report.ts, ctx) }
                    .onSuccess {
                        val elapsed = Duration.between(startedAt, Instant.now())
                        metrics.predictionRun(feed.code, algorithmTag, TransitTrackMetrics.Outcome.SUCCESS, elapsed, it.size)
                    }.onFailure {
                        val elapsed = Duration.between(startedAt, Instant.now())
                        metrics.predictionRun(feed.code, algorithmTag, TransitTrackMetrics.Outcome.FAILED, elapsed, 0)
                        if (failure == null) failure = it else failure?.addSuppressed(it)
                    }.getOrNull()
                    ?.let { strategy.algorithm to it }
            }
        return Generation(horizon, generated, failure)
    }

    private fun writePredictions(
        session: StatelessSession,
        feed: AvlFeed,
        report: AvlReportRow,
        outcome: MatchOutcome.Matched,
        generation: Generation,
        now: Instant,
    ) {
        val horizon = generation.horizon ?: return
        writer.deleteOutOfHorizonPredictions(
            session,
            feed.code,
            report.vehicleId,
            outcome.tripRowId,
            horizon.firstOrNull { it.tripRowId == outcome.tripRowId }?.stopPathIndex ?: Int.MAX_VALUE,
            horizon.map { it.tripRowId }.toSet() + outcome.tripRowId,
        )

        val rows = ArrayList<VehiclePredictionUpsert>()
        val checkpoints = ArrayList<CheckpointCandidate>()
        for ((algorithm, predictions) in generation.generated) {
            for (p in predictions) {
                rows.add(
                    VehiclePredictionUpsert(
                        feedCode = feed.code,
                        vehicleId = report.vehicleId,
                        tripRowId = p.tripRowId,
                        blockPk = outcome.blockPk,
                        tripPatternId = p.tripPatternId,
                        stopPathIndex = p.stopPathIndex,
                        algorithm = algorithm,
                        predictedArrivalTs = p.predictedArrivalTs,
                        predictedDepartureTs = p.predictedDepartureTs,
                        actualArrivalTs = null,
                        actualDepartureTs = null,
                        confidenceSec = p.confidenceSec,
                        computedAt = now,
                    ),
                )
                checkpointOf(feed, report, algorithm, p)?.let(checkpoints::add)
            }
        }
        writer.upsertPredictions(
            session,
            rows,
            staleBefore = now.minus(predictionLifetime()),
            refreshBefore = now.minusSeconds(props.run.predictionRefreshSec.toLong()),
            minChangeSec = props.run.predictionMinChangeSec,
        )
        writer.insertCheckpoints(session, checkpoints, report.ts.minus(predictionLifetime()))
    }

    /**
     * The accuracy checkpoint [p] falls in, if any: the shortest horizon at least as long as its
     * lead time (how far ahead of [report] it predicts the vehicle at the stop). A stop already
     * overdue counts as the shortest horizon.
     */
    private fun checkpointOf(
        feed: AvlFeed,
        report: AvlReportRow,
        algorithm: PredictionAlgorithm,
        p: GeneratedPrediction,
    ): CheckpointCandidate? {
        val predictedTs = (if (p.stopPathIndex == 0) p.predictedDepartureTs else p.predictedArrivalTs) ?: return null
        val leadSec = Duration.between(report.ts, predictedTs).seconds
        val horizonSec = horizonsSec.firstOrNull { leadSec <= it } ?: return null
        return CheckpointCandidate(
            feedCode = feed.code,
            vehicleId = report.vehicleId,
            tripRowId = p.tripRowId,
            stopPathIndex = p.stopPathIndex,
            algorithm = algorithm,
            horizonSec = horizonSec,
            predictedTs = predictedTs,
            computedAt = report.ts,
        )
    }

    private fun predictionLifetime(): Duration = Duration.ofHours(props.retention.predictionHours)

    /** One stop-to-stop time observed for stop path [stopPathIndex], bucketed by when it started. */
    private class LearningSample(
        val stopPathIndex: Int,
        val bucket: TravelTimeBucket,
        val sampleSec: Double,
        val seedSec: Double,
    )

    /**
     * Records every stop passed between the previous match and this one. A pass whose previous stop
     * on the same trip was seen at an earlier report yields one stop-to-stop sample (time from passing
     * the previous stop to reaching this one), which is what the learners and the optimizer consume;
     * the first stop counts as passed when the vehicle leaves it. Two consecutive stops passed between
     * the same two reports yield none: both pass times are interpolated along that interval, so the
     * time between them is just distance over the interval's average speed, with no dwell in it.
     *
     * A frequency-based trip row is run over and over through the day: passing a stop behind the
     * furthest one already passed starts a new run, which forgets the previous run's passes.
     */
    private fun recordStopPasses(
        session: StatelessSession,
        feed: AvlFeed,
        report: AvlReportRow,
        outcome: MatchOutcome.Matched,
        trip: TripRef,
        serviceDate: LocalDate,
        ctx: AvlMatchContext,
        detected: DetectedPasses,
        now: Instant,
    ) {
        val maxPlausibleSec = props.learn.maxPlausibleTravelTimeSec
        // Same trip row on an earlier service day, or a stale pass, is never this trip's previous stop.
        val since = report.ts.minusSeconds(maxPlausibleSec.toLong())
        val passedAt = HashMap(writer.lastPasses(session, feed.code, report.vehicleId, outcome.tripRowId, since))
        val passedHere = LinkedHashMap<Int, Instant>()
        val samples = ArrayList<LearningSample>()
        val crossings = ArrayList<AvlStopCrossing>()

        for (pass in detected.passes) {
            val k = pass.stopPathIndex
            if (trip.frequencyBased == true && passedAt.keys.any { it > k }) {
                passedAt.clear()
                writer.clearPreviousRun(session, feed.code, report.vehicleId, outcome.tripRowId)
            }
            // GPS jitter can carry a vehicle across the same stop twice; only the first pass counts.
            if (k in passedAt) continue

            val previousPassAt = if (k == 0 || (k - 1) in passedHere) null else passedAt[k - 1]
            val sampleSec =
                previousPassAt
                    ?.let { Duration.between(it, pass.passedAt).toMillis() / 1000.0 }
                    ?.takeIf { it > 0.0 && it <= maxPlausibleSec }
            if (sampleSec != null && previousPassAt != null) {
                // Bucketed by when the vehicle left the previous stop, the same moment predictions
                // look a stop path's time up by.
                val bucket = TravelTimeBucket.at(serviceDate, ctx.zone, previousPassAt)
                samples.add(LearningSample(k, bucket, sampleSec, seedStopToStopSec(detected.seeds, k) ?: sampleSec))
            }

            crossings.add(
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
            passedAt[k] = pass.passedAt
            passedHere[k] = pass.passedAt
        }

        if (detected.learningKey != null && samples.isNotEmpty()) {
            learn(session, detected.learningKey, samples, now)
            metrics.predictionLearningSamples(feed.code, samples.size)
        }
        writer.insertCrossings(session, crossings)
        writer.fillActuals(session, feed.code, report.vehicleId, outcome.tripRowId, passedHere)
        scoreCheckpoints(session, feed, report.vehicleId, outcome.tripRowId, passedHere, now)
        metrics.predictionCrossings(feed.code, crossings.size)
    }

    /**
     * Updates both learners with every sample of a report, each into its bucket and that bucket's
     * coarser roll-ups (its day type, and overall): all observation rows first, then all Kalman state
     * rows, as [PredictionWriter.lockObservations] requires.
     */
    private fun learn(
        session: StatelessSession,
        key: LearningKey,
        samples: List<LearningSample>,
        now: Instant,
    ) {
        val rows = samples.flatMap { s ->
            s.bucket
                .withRollups()
                .distinct()
                .map { LearnerBucket(s.stopPathIndex, it) to s }
        }

        val observations = writer.lockObservations(session, key, rows.map { it.first })
        val newObservations =
            rows.associate { (b, s) ->
                b to
                    updateRunningAverage(
                        observations[b],
                        s.seedSec,
                        s.sampleSec,
                        props.learn.historyWindow,
                        props.learn.historyPriorSamples,
                        clip,
                    )
            }
        writer.upsertObservations(session, key, newObservations, now)

        val states = writer.lockKalmanStates(session, key, rows.map { it.first })
        val newStates =
            rows.associate { (b, s) ->
                b to
                    updateKalman(
                        states[b],
                        s.seedSec,
                        props.learn.kalmanInitialVarianceSec2,
                        props.learn.kalmanMeasurementNoiseSec2,
                        props.learn.kalmanProcessNoiseSec2,
                        s.sampleSec,
                        clip,
                    )
            }
        writer.upsertKalmanStates(session, key, newStates, now)
    }

    /**
     * Scores the accuracy checkpoints kept for the stops the vehicle just passed, at the times in
     * [passedAt]: one `prediction_accuracy` row per stop, algorithm and horizon. A checkpoint older
     * than a prediction's lifetime belongs to the same trip row on an earlier service day and is
     * dropped unscored.
     */
    private fun scoreCheckpoints(
        session: StatelessSession,
        feed: AvlFeed,
        vehicleId: String,
        tripRowId: Long,
        passedAt: Map<Int, Instant>,
        now: Instant,
    ) {
        val rows = ArrayList<PredictionAccuracy>()
        for (c in writer.takeCheckpoints(session, feed.code, vehicleId, tripRowId, passedAt.keys)) {
            val actualTs = passedAt[c.stopPathIndex] ?: continue
            if (c.computedAt.isBefore(actualTs.minus(predictionLifetime()))) continue
            val errorSec = Duration.between(c.predictedTs, actualTs).seconds.toInt()
            rows.add(
                PredictionAccuracy(
                    feedCode = feed.code,
                    vehicleId = vehicleId,
                    tripRowId = tripRowId,
                    stopPathIndex = c.stopPathIndex,
                    algorithm = c.algorithm,
                    predictedTs = c.predictedTs,
                    actualTs = actualTs,
                    errorSec = errorSec,
                    absErrorSec = abs(errorSec),
                    createdAt = now,
                    horizonSec = c.horizonSec,
                    predictedAt = c.computedAt,
                ),
            )
            metrics.predictionAccuracy(feed.code, c.algorithm.name.lowercase(), errorSec)
        }
        writer.insertAccuracy(session, rows)
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
