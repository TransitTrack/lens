package eu.transittrack.predict.generate

import java.time.Duration
import java.time.Instant
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt

data class LatenessPolicy(
    val scheduleToleranceSec: Int,
    val headwayFraction: Double,
)

data class LateAssessment(
    val late: Boolean,
    val probability: Double,
)

/**
 * Decides whether a predicted arrival is late and with what probability.
 * Returns null when the delay cannot be determined (no scheduled arrival).
 */
fun assessLateness(
    policy: LatenessPolicy,
    scheduledArrival: Instant?,
    predictedArrival: Instant,
    sigmaSec: Double,
    scheduledHeadwaySec: Int?,
): LateAssessment? {
    if (scheduledArrival == null) return null
    val delay = Duration.between(scheduledArrival, predictedArrival).toMillis() / 1000.0
    val margin =
        if (scheduledHeadwaySec != null) {
            policy.headwayFraction * scheduledHeadwaySec
        } else {
            policy.scheduleToleranceSec.toDouble()
        }
    val late = delay > margin
    val probability =
        if (sigmaSec <= 0.0) {
            if (late) 1.0 else 0.0
        } else {
            1.0 - normalCdf((margin - delay) / sigmaSec)
        }
    return LateAssessment(late, probability)
}

/** Standard normal CDF via Abramowitz-Stegun 7.1.26 erf approximation (abs error < 1.5e-7). */
private fun normalCdf(z: Double): Double {
    val x = abs(z) / sqrt(2.0)
    val t = 1.0 / (1.0 + 0.3275911 * x)
    val poly = ((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t - 0.284496736) * t + 0.254829592
    val erf = 1.0 - poly * t * exp(-x * x)
    return if (z >= 0) 0.5 * (1.0 + erf) else 0.5 * (1.0 - erf)
}
