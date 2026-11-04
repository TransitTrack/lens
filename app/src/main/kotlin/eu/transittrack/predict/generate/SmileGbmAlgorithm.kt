package eu.transittrack.predict.generate

import java.time.Instant
import java.time.LocalDate
import kotlin.math.roundToInt

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

import eu.transittrack.avl.match.MatchContext
import eu.transittrack.avl.match.cache.CachedTravelTimesReader
import eu.transittrack.predict.PredictionAlgorithm
import eu.transittrack.predict.smile.SmileModelStore
import eu.transittrack.predict.smile.StopToStopFeatures
import eu.transittrack.predict.smile.StopToStopModel
import eu.transittrack.schedule.model.TripRef

/**
 * Accumulates each stop path's stop-to-stop time from the feed's gradient-boosted model
 * ([SmileModelStore]): the model's median is the leg's time, and the spread between its low and high
 * quantile (10th to 90th percentile, +-[Z_80] standard deviations) its variance. The walk is the
 * one the learned algorithms use ([walkHorizonWith]), so the first-stop hold and the dwell split are
 * the same.
 *
 * Each prediction with a `confidenceSec` also gets an ~80% range, `+-round(Z_80 * confidenceSec)`
 * seconds, and a late assessment ([ScheduledLateness]) against its scheduled arrival, with the
 * scheduled headway at that stop (as the `headway` read model computes it) as the margin where the
 * route has one; scoring judges the actual arrival against the same reference. A frequency-based
 * trip has no clock-time arrival, so it gets a range but no late assessment. Stop path 0 (a trip's
 * first stop, also a block continuation's) is a departure event and gets neither.
 *
 * Never throws for model reasons: with no model for the feed it predicts nothing; a stop path
 * without a schedule seed, or one the model can't estimate (rejected features, non-finite output),
 * ends the walk there. Inference only reads cached schedule data.
 */
@Component
@ConditionalOnProperty("transittrack.predict.enabled", havingValue = "true")
class SmileGbmAlgorithm(
    private val store: SmileModelStore,
    private val seeds: CachedTravelTimesReader,
    private val lateness: ScheduledLateness,
) : PredictionStrategy {
    private val log = LoggerFactory.getLogger(javaClass)

    override val algorithm = PredictionAlgorithm.SMILE_GBM

    /** The last model whose estimate failed, so a broken model is logged once, not every 5 s per vehicle. */
    @Volatile private var lastFailedModel: StopToStopModel? = null

    /** Without the AVL feed there is no model to pick. */
    override fun predict(
        horizon: List<HorizonStop>,
        serviceDate: LocalDate,
        adherenceSec: Int?,
        startTs: Instant,
        ctx: MatchContext,
    ): List<GeneratedPrediction> = emptyList()

    override fun predict(
        horizon: List<HorizonStop>,
        serviceDate: LocalDate,
        adherenceSec: Int?,
        startTs: Instant,
        ctx: MatchContext,
        feedCode: String,
    ): List<GeneratedPrediction> {
        val model = store.current(feedCode) ?: return emptyList()
        val trips = HashMap<Long, TripRef?>()
        val tripOf = { tripRowId: Long -> trips.getOrPut(tripRowId) { ctx.trip(tripRowId) } }
        val stopIds = PatternStopIds(ctx)

        val walked =
            walkHorizonWith(horizon, serviceDate, startTs, ctx, seeds) { stop, _, passTs ->
                val seedSec =
                    seedStopToStopSec(seeds.byStopPath(ctx.revisionId, stop.tripPatternId), stop.stopPathIndex)
                        ?: return@walkHorizonWith null
                val trip = tripOf(stop.tripRowId)
                val features =
                    StopToStopFeatures.featuresFor(
                        serviceDate = serviceDate,
                        zone = ctx.zone,
                        leftPreviousStopAt = passTs,
                        stopPathIndex = stop.stopPathIndex,
                        seedSec = seedSec,
                        tripStartSec = trip?.startTimeSec,
                        directionId = trip?.directionId,
                        routeId = trip?.routeId,
                        routeIndex = model.routeIndex,
                    )
                leg(model, features)
            }

        return walked.map { p -> withRangeAndLateness(p, serviceDate, ctx, tripOf(p.tripRowId), stopIds) }
    }

    /** The model's leg, or `null` (ending the walk) when it can't give a finite, non-negative one. */
    private fun leg(
        model: StopToStopModel,
        features: DoubleArray,
    ): LearnedStopToStop? {
        val estimate =
            try {
                model.estimate(features)
            } catch (e: RuntimeException) {
                if (lastFailedModel !== model) {
                    lastFailedModel = model
                    log.warn("SMILE_GBM: the model can't estimate a stop path; predictions stop there", e)
                }
                return null
            }
        val spreadSec = (estimate.highSec - estimate.lowSec) / (2 * Z_80)
        val varianceSec2 = spreadSec * spreadSec
        if (!estimate.medianSec.isFinite() || estimate.medianSec < 0.0 || !varianceSec2.isFinite()) return null
        return LearnedStopToStop(estimate.medianSec, varianceSec2, Long.MAX_VALUE)
    }

    private fun withRangeAndLateness(
        p: GeneratedPrediction,
        serviceDate: LocalDate,
        ctx: MatchContext,
        trip: TripRef?,
        stopIds: PatternStopIds,
    ): GeneratedPrediction {
        // Stop path 0 is a departure event (its snapshot and its crossing are departures), so a range
        // and late flag anchored on the arrival there would be scored against the wrong event.
        if (p.stopPathIndex == 0) return p
        val confidenceSec = p.confidenceSec ?: return p
        val rangeHighSec = (Z_80 * confidenceSec).roundToInt()
        val lateness = lateness(p, confidenceSec, serviceDate, ctx, trip, stopIds)
        return p.copy(
            rangeLowSec = -rangeHighSec,
            rangeHighSec = rangeHighSec,
            lateProbability = lateness?.probability,
            late = lateness?.late,
        )
    }

    /** `null` when the delay is unknown ([ScheduledLateness.reference]) or there is no predicted arrival. */
    private fun lateness(
        p: GeneratedPrediction,
        confidenceSec: Int,
        serviceDate: LocalDate,
        ctx: MatchContext,
        trip: TripRef?,
        stopIds: PatternStopIds,
    ): LateAssessment? {
        val predictedArrival = p.predictedArrivalTs ?: return null
        val reference = lateness.reference(ctx, serviceDate, trip, p.stopPathIndex, stopIds) ?: return null
        return lateness.assess(reference, predictedArrival, confidenceSec.toDouble())
    }

    private companion object {
        /** Standard normal quantile of the 90th percentile: the range covers ~80% of outcomes. */
        const val Z_80 = 1.2816
    }
}
