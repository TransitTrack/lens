package eu.transittrack.predict

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

import org.hibernate.StatelessSession
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

import eu.transittrack.avl.AvlProperties
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
import eu.transittrack.predict.generate.serviceSecToInstant
import eu.transittrack.predict.learn.LearningKey
import eu.transittrack.predict.learn.SampleClip
import eu.transittrack.predict.learn.StopPass
import eu.transittrack.predict.learn.TravelTimeBucket
import eu.transittrack.predict.learn.detectStopPasses
import eu.transittrack.predict.learn.stopReachedAtM
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
 * shared report-time snapshots for actual-lead accuracy evaluation.
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
    private val clock: Clock,
    private val avlProps: AvlProperties,
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
        val reportGapSec: Int,
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
        val receivedAt = clock.instant()

        val detected = if (prev != null && prev.tripRowId == outcome.tripRowId) detectPasses(report, prev, outcome, ctx) else null
        val generation =
            if (generatePredictions && props.run.isFreshSource(report.ts, receivedAt)) {
                generate(feed, report, outcome, serviceDate, ctx)
            } else {
                null
            }
        val now = clock.instant()

        // Everything below writes through the same StatelessSession/transaction: a matched report
        // can fan out into hundreds of rows (per horizon stop, per algorithm, plus passes/learning),
        // and committing each one separately made that commit's fsync the dominant cost of the whole
        // prediction pipeline. Each set of rows goes out as one batch.
        writer.inTransaction { session ->
            val runStartedAt = resolveFrequencyRun(session, feed, report, prev, outcome, trip, serviceDate, ctx)
            if (detected != null) recordStopPasses(session, feed, report, outcome, trip, serviceDate, ctx, detected, runStartedAt, now)
            if (generation != null) {
                val producedAt = clock.instant()
                if (props.run.isFreshSource(report.ts, producedAt)) {
                    writePredictions(session, feed, report, outcome, serviceDate, generation, producedAt)
                }
            }
        }
        generation?.failure?.let { throw it }
    }

    private fun resolveFrequencyRun(
        session: StatelessSession,
        feed: AvlFeed,
        report: AvlReportRow,
        prev: PrevVehicleMatch?,
        outcome: MatchOutcome.Matched,
        trip: TripRef,
        serviceDate: LocalDate,
        ctx: AvlMatchContext,
    ): Instant {
        val serviceStart = serviceSecToInstant(serviceDate, 0, ctx.zone)
        if (trip.frequencyBased != true) return serviceStart
        val state = writer.frequencyRun(session, feed.code, report.vehicleId, trip.id)
        if (state != null && report.ts.isBefore(state.lastReportTs)) {
            check(!report.ts.isBefore(state.startedAt)) { "Report ${report.ts} precedes the current frequency run ${state.startedAt}" }
            return state.startedAt
        }
        val previousDistance = state?.lastDistanceAlongTripM ?: prev?.takeIf { it.tripRowId == trip.id }?.distanceAlongTripM
        val recent = if (previousDistance == null) {
            writer.passObservations(
                session, feed.code, report.vehicleId, trip.id, serviceStart, report.ts,
            )
        } else {
            emptyList()
        }
        val priorDistance = previousDistance
            ?: recent.lastOrNull()?.let { crossing ->
                ctx.patternGeometry(outcome.tripPatternId)?.let {
                    stopReachedAtM(crossing.stopPathIndex, it.stopPathCumM, it.line.lengthM)
                }
            }
        val reset = state?.let { it.serviceDate != serviceDate } == true ||
            (state?.startTimeSec != null && report.descStartTimeSec != null && state.startTimeSec != report.descStartTimeSec) ||
            (priorDistance != null && priorDistance - outcome.distanceAlongTripM > avlProps.match.backtrackToleranceM)
        val startedAt = if (reset) report.ts else state?.startedAt ?: recent.lastOrNull()?.runStartedAt ?: serviceStart
        val startTimeSec = report.descStartTimeSec ?: state?.takeUnless { reset }?.startTimeSec
        if (reset) writer.clearPreviousRun(session, feed.code, report.vehicleId, trip.id)
        writer.saveFrequencyRun(
            session, feed.code, report.vehicleId, trip.id,
            FrequencyRunState(
                serviceDate, startedAt, report.ts, startTimeSec, outcome.distanceAlongTripM,
            ),
        )
        return startedAt
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
        val reportGap = Duration.between(prev.reportTs, report.ts)
        return DetectedPasses(
            passes,
            learningKeys.keyOf(ctx.revisionId, outcome.tripPatternId),
            travelTimes.byStopPath(ctx.revisionId, outcome.tripPatternId),
            Math.toIntExact(reportGap.seconds + if (reportGap.nano == 0) 0 else 1),
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
            feed.predictionAlgorithms.distinct().mapNotNull { algorithm ->
                val startedAt = Instant.now()
                val algorithmTag = algorithm.name.lowercase()
                runCatching {
                    val strategy = checkNotNull(strategies[algorithm]) { "No prediction strategy registered for $algorithm" }
                    strategy.predict(horizon, serviceDate, outcome.scheduleAdherenceSec, report.ts, ctx)
                }.onSuccess {
                    val elapsed = Duration.between(startedAt, Instant.now())
                    metrics.predictionRun(feed.code, algorithmTag, TransitTrackMetrics.Outcome.SUCCESS, elapsed, it.size)
                }.onFailure {
                    val elapsed = Duration.between(startedAt, Instant.now())
                    metrics.predictionRun(feed.code, algorithmTag, TransitTrackMetrics.Outcome.FAILED, elapsed, 0)
                    if (failure == null) failure = it else failure?.addSuppressed(it)
                }.getOrNull()
                    ?.let { algorithm to it }
            }
        return Generation(horizon, generated, failure)
    }

    private fun writePredictions(
        session: StatelessSession,
        feed: AvlFeed,
        report: AvlReportRow,
        outcome: MatchOutcome.Matched,
        serviceDate: LocalDate,
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
            report.ts,
        )

        val rows = ArrayList<VehiclePredictionUpsert>()
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
                        sourceReportTs = report.ts,
                        serviceDate = serviceDate,
                    ),
                )
            }
        }
        writer.upsertPredictions(
            session,
            rows,
            staleBefore = now.minus(predictionLifetime()),
            refreshBefore = now.minusSeconds(props.run.predictionRefreshSec.toLong()),
            minChangeSec = props.run.predictionMinChangeSec,
        )
        val interval = props.accuracy.sampleIntervalSec.toLong()
        val sampleSlot = Instant.ofEpochSecond(Math.floorDiv(report.ts.epochSecond, interval) * interval)
        val generated = generation.generated.associate { (algorithm, predictions) ->
            algorithm to predictions.associateBy { it.tripRowId to it.stopPathIndex }
        }
        val checkpoints = feed.predictionAlgorithms.distinct().flatMap { algorithm ->
            horizon.map { stop ->
                val p = generated[algorithm]?.get(stop.tripRowId to stop.stopPathIndex)
                CheckpointCandidate(
                    feedCode = feed.code,
                    vehicleId = report.vehicleId,
                    tripRowId = stop.tripRowId,
                    stopPathIndex = stop.stopPathIndex,
                    algorithm = algorithm,
                    predictedTs = if (stop.stopPathIndex == 0) p?.predictedDepartureTs else p?.predictedArrivalTs,
                    computedAt = report.ts,
                    sampleSlot = sampleSlot,
                    confidenceSec = p?.confidenceSec,
                    serviceDate = serviceDate,
                    producedAt = now,
                )
            }
        }
        writer.insertCheckpoints(
            session,
            checkpoints,
            now.minusSeconds(horizonsSec.last().toLong() + interval),
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
        runStartedAt: Instant,
        now: Instant,
    ) {
        val maxPlausibleSec = props.learn.maxPlausibleTravelTimeSec
        // Same trip row on an earlier service day, or a stale pass, is never this trip's previous stop.
        val serviceStart = serviceSecToInstant(serviceDate, 0, ctx.zone)
        val since = maxOf(report.ts.minusSeconds(maxPlausibleSec.toLong()), serviceStart, runStartedAt)
        val recent = writer.passObservations(session, feed.code, report.vehicleId, outcome.tripRowId, since, report.ts)
        val passedAt = recent.filter { !it.observedAt.isBefore(runStartedAt) }.associateBy { it.stopPathIndex }.toMutableMap()
        val passedHere = LinkedHashMap<Int, Instant>()
        val samples = ArrayList<LearningSample>()
        val crossings = ArrayList<AvlStopCrossing>()
        val reliable = detected.reportGapSec <= props.learn.maxReliableReportGapSec

        for (pass in detected.passes) {
            if (pass.passedAt.isBefore(runStartedAt)) continue
            val k = pass.stopPathIndex
            // GPS jitter can carry a vehicle across the same stop twice; only the first pass counts.
            if (k in passedAt) continue

            val previousPass = if (k == 0 || (k - 1) in passedHere) null else passedAt[k - 1]
            val previousPassAt =
                previousPass
                    ?.takeIf {
                        reliable && (it.reportGapSec?.let { gap -> gap <= props.learn.maxReliableReportGapSec } == true)
                    }?.observedAt
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

            val crossing = AvlStopCrossing(
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
                runStartedAt = runStartedAt,
                reportGapSec = detected.reportGapSec,
            )
            crossings.add(crossing)
            passedAt[k] = crossing
            passedHere[k] = pass.passedAt
        }

        if (detected.learningKey != null && samples.isNotEmpty()) {
            learn(session, detected.learningKey, samples, report.ts)
            metrics.predictionLearningSamples(feed.code, samples.size)
        }
        writer.insertCrossings(session, crossings)
        writer.fillActuals(session, feed.code, report.vehicleId, outcome.tripRowId, passedHere, detected.reportGapSec, serviceDate)
        scoreCheckpoints(session, feed, report.vehicleId, trip, serviceDate, ctx, passedHere, reliable, now)
        metrics.predictionCrossings(feed.code, crossings.size)
        if (!reliable) metrics.predictionUnreliableCrossings(feed.code, crossings.size)
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

        val states = writer.lockKalmanStates(session, key, rows.map { it.first }, now, props.learn.kalmanVarianceGrowthSec2PerHour)
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
     * [passedAt]. Horizons use actual lead time, never an algorithm's predicted ETA. Within each
     * horizon, all algorithms use the same earliest snapshot, including explicitly missing forecasts.
     * Crossings interpolated over excessive report gaps are retained but not treated as ground truth.
     */
    private fun scoreCheckpoints(
        session: StatelessSession,
        feed: AvlFeed,
        vehicleId: String,
        trip: TripRef,
        serviceDate: LocalDate,
        ctx: AvlMatchContext,
        passedAt: Map<Int, Instant>,
        reliable: Boolean,
        now: Instant,
    ) {
        val checkpoints = writer.takeCheckpoints(session, feed.code, vehicleId, trip.id, passedAt.keys)
        if (!reliable) return
        val rows = ArrayList<PredictionAccuracy>()
        for ((stopIndex, snapshots) in checkpoints.groupBy { it.stopPathIndex }) {
            val actualTs = passedAt.getValue(stopIndex)
            val cohorts = snapshots
                .filter {
                    it.serviceDate == serviceDate && it.producedAt?.let { at -> !at.isAfter(actualTs) } == true
                }.groupBy { Triple(it.sampleSlot, it.computedAt, checkNotNull(it.producedAt)) }
            var shorterHorizon = -1
            for (horizonSec in horizonsSec) {
                val cohort = cohorts.entries
                    .filter {
                        Duration.between(it.key.third, actualTs).seconds in (shorterHorizon + 1).toLong()..horizonSec.toLong()
                    }.minByOrNull { it.key.third }
                shorterHorizon = horizonSec
                if (cohort == null) continue
                for (c in cohort.value) {
                    val errorSec = c.predictedTs?.let { Math.toIntExact(Duration.between(it, actualTs).seconds) }
                    val bucket = TravelTimeBucket.at(serviceDate, ctx.zone, c.computedAt)
                    rows.add(
                        PredictionAccuracy(
                            feedCode = feed.code,
                            vehicleId = vehicleId,
                            tripRowId = trip.id,
                            stopPathIndex = stopIndex,
                            algorithm = c.algorithm,
                            predictedTs = c.predictedTs,
                            actualTs = actualTs,
                            errorSec = errorSec,
                            absErrorSec = errorSec?.let { Math.absExact(it) },
                            createdAt = now,
                            horizonSec = horizonSec,
                            predictedAt = c.computedAt,
                            actualLeadSec = Math.toIntExact(Duration.between(cohort.key.third, actualTs).seconds),
                            producedAt = cohort.key.third,
                            confidenceSec = c.confidenceSec,
                            routeId = trip.routeId,
                            directionId = trip.directionId,
                            dayType = bucket.dayType,
                            timeBand = bucket.timeBand,
                        ),
                    )
                    if (errorSec != null) metrics.predictionAccuracy(feed.code, c.algorithm.name.lowercase(), errorSec)
                }
            }
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
