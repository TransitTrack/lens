package eu.transittrack.predict.generate

import java.time.Instant
import java.time.LocalDate

import eu.transittrack.avl.match.MatchContext
import eu.transittrack.predict.PredictionAlgorithm

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
