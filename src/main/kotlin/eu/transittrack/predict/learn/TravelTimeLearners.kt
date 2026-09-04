package eu.transittrack.predict.learn

data class RunningAverageState(
    val sampleCount: Long,
    val meanSec: Double,
)

fun updateRunningAverage(
    prev: RunningAverageState?,
    seed: Double,
    sample: Double,
): RunningAverageState {
    val base = prev ?: RunningAverageState(0, seed)
    val newCount = base.sampleCount + 1
    val newMean = base.meanSec + (sample - base.meanSec) / newCount
    return RunningAverageState(newCount, newMean)
}

data class KalmanState(
    val estimateSec: Double,
    val errorVariance: Double,
    val sampleCount: Long,
)

fun updateKalman(
    prev: KalmanState?,
    seed: Double,
    initialVariance: Double,
    measurementNoise: Double,
    sample: Double,
): KalmanState {
    val base = prev ?: KalmanState(seed, initialVariance, 0)
    val gain = base.errorVariance / (base.errorVariance + measurementNoise)
    val estimate = base.estimateSec + gain * (sample - base.estimateSec)
    val variance = base.errorVariance * measurementNoise / (base.errorVariance + measurementNoise)
    return KalmanState(estimate, variance, base.sampleCount + 1)
}
