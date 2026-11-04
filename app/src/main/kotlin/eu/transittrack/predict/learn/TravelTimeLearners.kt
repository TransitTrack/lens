package eu.transittrack.predict.learn

/**
 * Bounds how far a single sample can sit from the current estimate before a learner takes it in:
 * `max(minDeviationSec, maxRelativeDeviation * estimate)`. Clipping rather than discarding keeps a
 * lasting change (road works, a detour) learnable, just over a few samples instead of one. The
 * first sample is never clipped: the schedule seed it would be measured against is no learned value.
 */
data class SampleClip(
    val minDeviationSec: Double,
    val maxRelativeDeviation: Double,
) {
    fun clip(
        sample: Double,
        estimate: Double,
    ): Double {
        val maxDeviation = maxOf(minDeviationSec, maxRelativeDeviation * estimate)
        return sample.coerceIn(estimate - maxDeviation, estimate + maxDeviation)
    }
}

data class RunningAverageState(
    val sampleCount: Long,
    val meanSec: Double,
)

/**
 * Running mean that stops being a plain cumulative average after [window] samples: from then on
 * each new sample keeps weight `1/window`, so the mean keeps tracking changing conditions instead
 * of freezing on its whole history. The [seed] counts as [priorSamples] samples, so the first few
 * observations blend with it rather than the first one replacing it.
 */
fun updateRunningAverage(
    prev: RunningAverageState?,
    seed: Double,
    sample: Double,
    window: Int,
    priorSamples: Int = 0,
    clip: SampleClip? = null,
): RunningAverageState {
    val base = prev ?: RunningAverageState(0, seed)
    val newCount = base.sampleCount + 1
    val weight = minOf(newCount + priorSamples.coerceAtLeast(0), window.toLong().coerceAtLeast(1))
    val taken = if (prev == null) sample else clip?.clip(sample, base.meanSec) ?: sample
    val newMean = base.meanSec + (taken - base.meanSec) / weight
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
 * the estimate stops learning. The gain settles around 0.2 with the default noises, so an unclipped
 * outlier would drag the estimate a fifth of the way towards it - hence [clip].
 */
fun updateKalman(
    prev: KalmanState?,
    seed: Double,
    initialVariance: Double,
    measurementNoise: Double,
    processNoise: Double,
    sample: Double,
    clip: SampleClip? = null,
): KalmanState {
    val base = prev ?: KalmanState(seed, initialVariance, 0)
    val prior = if (prev == null) base.errorVariance else base.errorVariance + processNoise
    val gain = prior / (prior + measurementNoise)
    val taken = if (prev == null) sample else clip?.clip(sample, base.estimateSec) ?: sample
    val estimate = base.estimateSec + gain * (taken - base.estimateSec)
    val variance = prior * measurementNoise / (prior + measurementNoise)
    return KalmanState(estimate, variance, base.sampleCount + 1)
}
