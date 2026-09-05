package eu.transittrack.predict.generate

import java.time.Instant
import java.time.LocalDate
import kotlin.math.roundToInt
import kotlin.math.sqrt

import org.springframework.stereotype.Component

import eu.transittrack.avl.match.AvlMatchContext
import eu.transittrack.predict.PredictionAlgorithm
import eu.transittrack.predict.model.KalmanTravelTimeStateRepository
import eu.transittrack.predict.model.TravelTimeObservationRepository
import eu.transittrack.schedule.model.TravelTimesForStopPathRepository

/**
 * The schedule-derived travel time for `(tripPatternId, stopPathIndex)`, used as a fallback when
 * no learned row exists yet for that stop path. Dwell is always read from this same seed table
 * since dwell isn't learned (per spec §5.2).
 */
private fun seedTravelTimeSec(
    ctx: AvlMatchContext,
    seeds: TravelTimesForStopPathRepository,
    tripPatternId: Long,
    stopPathIndex: Int,
): Double? =
    seeds
        .findByTripPatternOrdered(ctx.revisionId, tripPatternId)
        .firstOrNull { it.stopPathIndex == stopPathIndex }
        ?.travelTimeSec
        ?.toDouble()

private fun seedDwellTimeSec(
    ctx: AvlMatchContext,
    seeds: TravelTimesForStopPathRepository,
    tripPatternId: Long,
    stopPathIndex: Int,
): Int? =
    seeds
        .findByTripPatternOrdered(ctx.revisionId, tripPatternId)
        .firstOrNull { it.stopPathIndex == stopPathIndex }
        ?.dwellTimeSec

/**
 * Anchors the accumulation walk at the first horizon stop's own scheduled time, if one exists;
 * falls back to service-day start otherwise. The 4-arg `PredictionStrategy` interface has no room
 * for "the vehicle's current position timestamp" (that's threaded in by the caller in a later
 * task, via which horizon it passes), so this is the best available anchor from within `predict`.
 */
private fun startTs(
    horizon: List<HorizonStop>,
    serviceDate: LocalDate,
    ctx: AvlMatchContext,
): Instant {
    val first = horizon.firstOrNull() ?: return serviceDate.atStartOfDay(ctx.zone).toInstant()
    val schedulePoint = ctx.scheduleOf(first.tripRowId).firstOrNull { it.stopPathIndex == first.stopPathIndex }
    val sec = schedulePoint?.arrivalSec ?: schedulePoint?.departureSec
    return if (sec != null) serviceSecToInstant(serviceDate, sec, ctx.zone) else serviceDate.atStartOfDay(ctx.zone).toInstant()
}

/**
 * Predicts arrival/departure by walking the horizon and accumulating each stop path's learned
 * mean travel time (falling back to the schedule-derived seed when no observation exists yet).
 */
@Component
class HistoricalAverageAlgorithm(
    private val observations: TravelTimeObservationRepository,
    private val seeds: TravelTimesForStopPathRepository,
) : PredictionStrategy {
    override val algorithm = PredictionAlgorithm.HISTORICAL_AVERAGE

    override fun predict(
        horizon: List<HorizonStop>,
        serviceDate: LocalDate,
        adherenceSec: Int,
        ctx: AvlMatchContext,
    ): List<GeneratedPrediction> {
        var lastTs = startTs(horizon, serviceDate, ctx)
        var lastTripRowId: Long? = null
        val out = ArrayList<GeneratedPrediction>()
        for (stop in horizon) {
            val previousTripRowId = lastTripRowId
            if (previousTripRowId != null && previousTripRowId != stop.tripRowId) {
                val layover = ctx.blockTripOf(previousTripRowId)?.layoverAfterSec ?: 0
                lastTs = lastTs.plusSeconds(layover.toLong())
            }
            val travelSec =
                observations.findByTripPatternIdAndStopPathIndex(stop.tripPatternId, stop.stopPathIndex)?.meanSec
                    ?: seedTravelTimeSec(ctx, seeds, stop.tripPatternId, stop.stopPathIndex)
                    ?: continue
            lastTs = lastTs.plusSeconds(travelSec.toLong())
            val dwellSec = seedDwellTimeSec(ctx, seeds, stop.tripPatternId, stop.stopPathIndex)
            val departureTs = lastTs.plusSeconds((dwellSec ?: 0).toLong())
            out.add(
                GeneratedPrediction(
                    stop.stopPathIndex,
                    stop.tripRowId,
                    stop.tripPatternId,
                    lastTs,
                    departureTs,
                    confidenceSec = null,
                ),
            )
            lastTripRowId = stop.tripRowId
            lastTs = departureTs
        }
        return out
    }
}

/**
 * Predicts arrival/departure by walking the horizon and accumulating each stop path's learned
 * Kalman travel time estimate (falling back to the schedule-derived seed when no state exists
 * yet). Reports `confidenceSec` as `sqrt(errorVariance)` when a Kalman state row is present.
 */
@Component
class KalmanAlgorithm(
    private val states: KalmanTravelTimeStateRepository,
    private val seeds: TravelTimesForStopPathRepository,
) : PredictionStrategy {
    override val algorithm = PredictionAlgorithm.KALMAN

    override fun predict(
        horizon: List<HorizonStop>,
        serviceDate: LocalDate,
        adherenceSec: Int,
        ctx: AvlMatchContext,
    ): List<GeneratedPrediction> {
        var lastTs = startTs(horizon, serviceDate, ctx)
        var lastTripRowId: Long? = null
        val out = ArrayList<GeneratedPrediction>()
        for (stop in horizon) {
            val previousTripRowId = lastTripRowId
            if (previousTripRowId != null && previousTripRowId != stop.tripRowId) {
                val layover = ctx.blockTripOf(previousTripRowId)?.layoverAfterSec ?: 0
                lastTs = lastTs.plusSeconds(layover.toLong())
            }
            val state = states.findByTripPatternIdAndStopPathIndex(stop.tripPatternId, stop.stopPathIndex)
            val travelSec =
                state?.estimateSec
                    ?: seedTravelTimeSec(ctx, seeds, stop.tripPatternId, stop.stopPathIndex)
                    ?: continue
            lastTs = lastTs.plusSeconds(travelSec.toLong())
            val dwellSec = seedDwellTimeSec(ctx, seeds, stop.tripPatternId, stop.stopPathIndex)
            val departureTs = lastTs.plusSeconds((dwellSec ?: 0).toLong())
            val confidenceSec = state?.errorVariance?.let { sqrt(it).roundToInt() }
            out.add(
                GeneratedPrediction(
                    stop.stopPathIndex,
                    stop.tripRowId,
                    stop.tripPatternId,
                    lastTs,
                    departureTs,
                    confidenceSec,
                ),
            )
            lastTripRowId = stop.tripRowId
            lastTs = departureTs
        }
        return out
    }
}
