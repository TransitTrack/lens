package eu.transittrack.predict.generate

import java.time.Instant
import java.time.LocalDate

import eu.transittrack.avl.match.MatchContext
import eu.transittrack.predict.PredictionAlgorithm

/**
 * One stop, on one trip, within a vehicle's remaining prediction horizon. [remainingFraction] is
 * the share of this stop's stop path the vehicle still has to cover: below 1.0 only for the first
 * horizon stop, the stop path the vehicle is currently on. [tripHop] is zero on the matched trip,
 * positive on a following block trip, even when the matched trip has no stops left in the horizon.
 */
data class HorizonStop(
    val tripRowId: Long,
    val tripPatternId: Long,
    val stopPathIndex: Int,
    val remainingFraction: Double = 1.0,
    val tripHop: Int = 0,
)

/** A single strategy's output for one stop; not implementing any shared marker interface. */
data class GeneratedPrediction(
    val stopPathIndex: Int,
    val tripRowId: Long,
    val tripPatternId: Long,
    val predictedArrivalTs: Instant?,
    val predictedDepartureTs: Instant?,
    val confidenceSec: Int?,
)

/** A pluggable per-vehicle prediction strategy: given a horizon, produces predictions for it. */
interface PredictionStrategy {
    val algorithm: PredictionAlgorithm

    /**
     * [startTs] is the vehicle's current position timestamp — the anchor accumulation-style
     * strategies walk forward from. [adherenceSec] is `null` when the match produced no usable
     * schedule adherence (unscheduled trip, or an implausible value); strategies that depend on it
     * should then predict nothing rather than assume the vehicle is on time.
     */
    fun predict(
        horizon: List<HorizonStop>,
        serviceDate: LocalDate,
        adherenceSec: Int?,
        startTs: Instant,
        ctx: MatchContext,
    ): List<GeneratedPrediction>
}
