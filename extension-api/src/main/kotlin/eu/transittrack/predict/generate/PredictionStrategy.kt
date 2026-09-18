package eu.transittrack.predict.generate

import java.time.Instant
import java.time.LocalDate

import eu.transittrack.avl.match.MatchContext
import eu.transittrack.predict.PredictionAlgorithm

/** One stop, on one trip, within a vehicle's remaining prediction horizon. */
data class HorizonStop(
    val tripRowId: Long,
    val tripPatternId: Long,
    val stopPathIndex: Int,
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
     * strategies walk forward from. Schedule-projection strategies (e.g.
     * `ScheduleAdherenceAlgorithm`) don't need it.
     */
    fun predict(
        horizon: List<HorizonStop>,
        serviceDate: LocalDate,
        adherenceSec: Int,
        startTs: Instant,
        ctx: MatchContext,
    ): List<GeneratedPrediction>
}
