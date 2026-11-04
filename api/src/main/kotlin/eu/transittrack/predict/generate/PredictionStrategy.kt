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

    /**
     * [predict] for a vehicle of the AVL feed [feedCode] (`avl_feed.code`), which is what the
     * prediction pipeline calls. [MatchContext] is per GTFS revision, which several AVL feeds can
     * share, so it doesn't carry the feed. A strategy whose output depends on the feed (such as a
     * model trained per feed) overrides this; the default ignores the feed.
     */
    fun predict(
        horizon: List<HorizonStop>,
        serviceDate: LocalDate,
        adherenceSec: Int?,
        startTs: Instant,
        ctx: MatchContext,
        feedCode: String,
    ): List<GeneratedPrediction> = predict(horizon, serviceDate, adherenceSec, startTs, ctx)

    /**
     * Whether this strategy can predict for the AVL feed [feedCode] at all, such as a trained model
     * existing for it. A strategy that isn't ready still runs, but its (empty) forecasts aren't
     * scored, so it doesn't count in the feed's accuracy comparison until it is.
     */
    fun isReady(feedCode: String): Boolean = true
}
