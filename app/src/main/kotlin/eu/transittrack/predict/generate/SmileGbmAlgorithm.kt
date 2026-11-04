package eu.transittrack.predict.generate

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlin.math.roundToInt

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

import eu.transittrack.avl.match.MatchContext
import eu.transittrack.avl.match.cache.CachedTravelTimesReader
import eu.transittrack.predict.PredictProperties
import eu.transittrack.predict.PredictionAlgorithm
import eu.transittrack.predict.smile.SmileModelStore
import eu.transittrack.predict.smile.StopToStopFeatures
import eu.transittrack.predict.smile.StopToStopModel
import eu.transittrack.schedule.model.ServiceStopTime
import eu.transittrack.schedule.model.TripRef

/**
 * Accumulates each stop path's stop-to-stop time from the feed's gradient-boosted model
 * ([SmileModelStore]): the model's median is the leg's time, and the spread between its low and high
 * quantile (10th to 90th percentile, +-[Z_80] standard deviations) its variance. The walk is the
 * one the learned algorithms use ([walkHorizonWith]), so the first-stop hold and the dwell split are
 * the same.
 *
 * Each prediction with a `confidenceSec` also gets an ~80% range, `+-round(Z_80 * confidenceSec)`
 * seconds, and a late assessment ([assessLateness]) against its scheduled arrival, with the
 * scheduled headway at that stop (as the `headway` read model computes it) as the margin where the
 * route has one. A frequency-based trip has no clock-time arrival, so it gets a range but no late
 * assessment.
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
    private val props: PredictProperties,
) : PredictionStrategy {
    private val log = LoggerFactory.getLogger(javaClass)

    override val algorithm = PredictionAlgorithm.SMILE_GBM

    private val policy = LatenessPolicy(props.late.scheduleToleranceSec, props.late.headwayFraction)

    /** The last model whose estimate failed, so a broken model is logged once, not every 5 s per vehicle. */
    @Volatile private var lastFailedModel: StopToStopModel? = null

    private data class RouteDay(
        val revisionId: Long,
        val routeId: String,
        val directionId: Int?,
        val serviceDate: LocalDate,
    )

    /** Clock-time trips' scheduled times by stop id, per route, direction and service date. */
    private val stopTimesByRouteDay: Cache<RouteDay, Map<String, List<ServiceStopTime>>> =
        Caffeine
            .newBuilder()
            .maximumSize(2_000)
            .expireAfterAccess(Duration.ofHours(2))
            .build()

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
        val stopIds = StopIds(ctx)

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
        stopIds: StopIds,
    ): GeneratedPrediction {
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

    /**
     * `null` for a frequency-based trip, or a stop without a scheduled time: the delay is unknown. The
     * scheduled arrival is the stop's arrival time, else its departure time, as the headway uses.
     */
    private fun lateness(
        p: GeneratedPrediction,
        confidenceSec: Int,
        serviceDate: LocalDate,
        ctx: MatchContext,
        trip: TripRef?,
        stopIds: StopIds,
    ): LateAssessment? {
        if (trip == null || trip.frequencyBased == true) return null
        val predictedArrival = p.predictedArrivalTs ?: return null
        val point = ctx.scheduleOf(p.tripRowId).firstOrNull { it.stopPathIndex == p.stopPathIndex } ?: return null
        val scheduledArrival =
            (point.arrivalSec ?: point.departureSec)?.let { serviceSecToInstant(serviceDate, it, ctx.zone) } ?: return null
        val headwaySec = scheduledHeadwaySec(ctx, serviceDate, trip, stopIds, p.stopPathIndex, scheduledArrival)
        return assessLateness(policy, scheduledArrival, predictedArrival, confidenceSec.toDouble(), headwaySec)
    }

    /**
     * The scheduled headway at the stop [trip] reaches at [stopPathIndex], around [scheduledArrival]:
     * the same average gap, over the same clock-time trips of the route and direction at that stop,
     * that the `headway` read model computes ([averageScheduledHeadwaySec]), read from the match
     * context's cached schedule rather than the database.
     */
    private fun scheduledHeadwaySec(
        ctx: MatchContext,
        serviceDate: LocalDate,
        trip: TripRef,
        stopIds: StopIds,
        stopPathIndex: Int,
        scheduledArrival: Instant,
    ): Int? {
        val patternId = trip.tripPatternId ?: return null
        val stopId = stopIds.of(patternId)[stopPathIndex] ?: return null
        val stopTimes =
            stopTimesByRouteDay
                .get(RouteDay(ctx.revisionId, trip.routeId, trip.directionId, serviceDate)) { stopTimesAtStops(ctx, it) }[stopId]
                ?: return null
        return averageScheduledHeadwaySec(
            stopTimes,
            scheduledArrival.minus(SCHEDULED_HEADWAY_MARGIN),
            scheduledArrival.plus(SCHEDULED_HEADWAY_MARGIN),
            ctx.zone,
            ctx::activeServiceIds,
        )
    }

    /**
     * Every clock-time trip of the route (and direction, when known) that can run in a headway
     * window around [RouteDay.serviceDate]'s times, by the stops it serves. Service-day times run
     * past 24:00, and the window reaches an hour either side, so services of two days before and
     * after count; [averageScheduledHeadwaySec] keeps only those running on each date.
     */
    private fun stopTimesAtStops(
        ctx: MatchContext,
        key: RouteDay,
    ): Map<String, List<ServiceStopTime>> {
        val services = (-2L..2L).flatMapTo(HashSet()) { ctx.activeServiceIds(key.serviceDate.plusDays(it)) }
        if (services.isEmpty()) return emptyMap()
        val stopIds = StopIds(ctx)
        val out = HashMap<String, MutableList<ServiceStopTime>>()
        for (t in ctx.candidateTripsForRoute(key.routeId, services)) {
            if (t.frequencyBased == true) continue
            if (key.directionId != null && t.directionId != key.directionId) continue
            val patternId = t.tripPatternId ?: continue
            val stopIdAt = stopIds.of(patternId)
            for (point in ctx.scheduleOf(t.id)) {
                val stopId = stopIdAt[point.stopPathIndex] ?: continue
                out.getOrPut(stopId) { ArrayList() }.add(ServiceStopTime(point.arrivalSec, point.departureSec, t.serviceId))
            }
        }
        return out
    }

    /** Each pattern's stop ids by stop path index, read from [ctx] once per use. */
    private class StopIds(
        private val ctx: MatchContext,
    ) {
        private val byPattern = HashMap<Long, Map<Int, String>>()

        fun of(patternId: Long): Map<Int, String> =
            byPattern.getOrPut(patternId) { ctx.patternStops(patternId).associate { it.stopPathIndex to it.stopId } }
    }

    private companion object {
        /** Standard normal quantile of the 90th percentile: the range covers ~80% of outcomes. */
        const val Z_80 = 1.2816
    }
}
