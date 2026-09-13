package eu.transittrack.schedule.optimize

import kotlin.math.abs

data class RobustSample(
    val values: List<Double>,
    val median: Double,
    val mad: Double,
)

object RobustStatistics {
    private const val MODIFIED_Z_SCORE_LIMIT = 3.5
    private const val MODIFIED_Z_SCORE_FACTOR = 0.6745

    fun filter(values: List<Double>): RobustSample {
        require(values.isNotEmpty()) { "at least one observation is required" }

        val median = medianOf(values)
        val mad = medianOf(values.map { abs(it - median) })
        val retained =
            if (mad == 0.0) {
                values.filter { it == median }
            } else {
                values.filter { abs(MODIFIED_Z_SCORE_FACTOR * (it - median) / mad) <= MODIFIED_Z_SCORE_LIMIT }
            }

        return RobustSample(retained, medianOf(retained), mad)
    }

    private fun medianOf(values: List<Double>): Double {
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[middle - 1] + sorted[middle]) / 2 else sorted[middle]
    }
}
