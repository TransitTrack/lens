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
 *
 * The margin is the schedule tolerance, tightened to [LatenessPolicy.headwayFraction] of the
 * scheduled headway where that is smaller: on a frequent route a delay of half a headway already
 * bunches vehicles, while a long-headway route keeps the tolerance. A [scheduledHeadwaySec] of zero
 * or less counts as unknown.
 */
fun assessLateness(
    policy: LatenessPolicy,
    scheduledArrival: Instant?,
    predictedArrival: Instant,
    sigmaSec: Double,
    scheduledHeadwaySec: Int?,
): LateAssessment? {
    if (scheduledArrival == null) return null
    val delay = delaySec(scheduledArrival, predictedArrival)
    val margin = latenessMarginSec(policy, scheduledHeadwaySec)
    val late = delay > margin
    val probability =
        if (sigmaSec <= 0.0) {
            if (late) 1.0 else 0.0
        } else {
            1.0 - normalCdf((margin - delay) / sigmaSec)
        }
    return LateAssessment(late, probability)
}

/**
 * Whether [arrival] is late against [scheduledArrival]: the same rule, and margin, [assessLateness]
 * judges a predicted arrival by, so an actual arrival can be scored against the assessment made for it.
 */
fun isLateArrival(
    policy: LatenessPolicy,
    scheduledArrival: Instant,
    arrival: Instant,
    scheduledHeadwaySec: Int?,
): Boolean = delaySec(scheduledArrival, arrival) > latenessMarginSec(policy, scheduledHeadwaySec)

/**
 * Seconds past the scheduled arrival tolerated before an arrival counts as late: the schedule
 * tolerance, or [LatenessPolicy.headwayFraction] of a known (positive) scheduled headway where that is smaller.
 */
fun latenessMarginSec(
    policy: LatenessPolicy,
    scheduledHeadwaySec: Int?,
): Double {
    val tolerance = policy.scheduleToleranceSec.toDouble()
    return if (scheduledHeadwaySec != null && scheduledHeadwaySec > 0) {
        minOf(tolerance, policy.headwayFraction * scheduledHeadwaySec)
    } else {
        tolerance
    }
}

private fun delaySec(
    scheduledArrival: Instant,
    arrival: Instant,
): Double = Duration.between(scheduledArrival, arrival).toMillis() / 1000.0

/** Standard normal CDF via Abramowitz-Stegun 7.1.26 erf approximation (abs error < 1.5e-7). */
private fun normalCdf(z: Double): Double {
    val x = abs(z) / sqrt(2.0)
    val t = 1.0 / (1.0 + 0.3275911 * x)
    val poly = ((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t - 0.284496736) * t + 0.254829592
    val erf = 1.0 - poly * t * exp(-x * x)
    return if (z >= 0) 0.5 * (1.0 + erf) else 0.5 * (1.0 - erf)
}
