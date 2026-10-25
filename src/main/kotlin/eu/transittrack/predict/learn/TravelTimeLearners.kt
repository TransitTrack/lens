package eu.transittrack.predict.learn

data class RunningAverageState(
    val sampleCount: Long,
    val meanSec: Double,
)

/**
 * Running mean that stops being a plain cumulative average after [window] samples: from then on
 * each new sample keeps weight `1/window`, so the mean keeps tracking changing conditions instead
 * of freezing on its whole history.
 */
fun updateRunningAverage(
    prev: RunningAverageState?,
    seed: Double,
    sample: Double,
    window: Int,
): RunningAverageState {
    val base = prev ?: RunningAverageState(0, seed)
    val newCount = base.sampleCount + 1
    val weight = minOf(newCount, window.toLong().coerceAtLeast(1))
    val newMean = base.meanSec + (sample - base.meanSec) / weight
    return RunningAverageState(newCount, newMean)
}

data class KalmanState(
    val estimateSec: Double,
    val errorVariance: Double,
    val sampleCount: Long,
)

/**
 * Scalar Kalman update. [processNoise] is added to an existing state's variance before each update:
 * the true travel time drifts over time, and without it the variance (and gain) decay to zero and
 * the estimate stops learning.
 */
fun updateKalman(
    prev: KalmanState?,
    seed: Double,
    initialVariance: Double,
    measurementNoise: Double,
    processNoise: Double,
    sample: Double,
): KalmanState {
    val base = prev ?: KalmanState(seed, initialVariance, 0)
    val prior = if (prev == null) base.errorVariance else base.errorVariance + processNoise
    val gain = prior / (prior + measurementNoise)
    val estimate = base.estimateSec + gain * (sample - base.estimateSec)
    val variance = prior * measurementNoise / (prior + measurementNoise)
    return KalmanState(estimate, variance, base.sampleCount + 1)
}
