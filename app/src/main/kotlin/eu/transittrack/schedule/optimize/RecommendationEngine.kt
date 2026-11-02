package eu.transittrack.schedule.optimize

sealed interface RecommendationCandidate {
    data class StopTime(
        val observedSec: Int,
        val deltaSec: Int,
        val sampleCount: Int,
    ) : RecommendationCandidate

    data class TripShift(
        val tripId: String,
        val observedStartSec: Int,
        val deltaSec: Int,
        val sampleCount: Int,
    ) : RecommendationCandidate
}

object RecommendationEngine {
    fun stopTime(
        scheduledSec: Int,
        observedSec: List<Double>,
        minimumSamples: Int,
        materialitySec: Int,
    ): RecommendationCandidate.StopTime? {
        val robust = RobustStatistics.filter(observedSec)
        if (robust.values.size < minimumSamples) return null

        val observed = robust.median.toInt()
        val delta = observed - scheduledSec
        if (kotlin.math.abs(delta) < materialitySec) return null

        return RecommendationCandidate.StopTime(observed, delta, robust.values.size)
    }

    fun tripShift(
        tripId: String,
        scheduledStartSec: Int,
        observedStartSec: List<Double>,
        predecessorStartSec: Int?,
        successorStartSec: Int?,
        targetHeadwaySec: Int,
        minimumSamples: Int,
        materialitySec: Int,
    ): RecommendationCandidate.TripShift? {
        val robust = RobustStatistics.filter(observedStartSec)
        if (robust.values.size < minimumSamples) return null
        val proposed = robust.median.toInt()
        val delta = proposed - scheduledStartSec
        if (kotlin.math.abs(delta) < materialitySec) return null
        if (predecessorStartSec != null && proposed <= predecessorStartSec) return null
        if (successorStartSec != null && proposed >= successorStartSec) return null

        fun deviation(start: Int) =
            listOfNotNull(
                predecessorStartSec?.let { kotlin.math.abs(start - it - targetHeadwaySec) },
                successorStartSec?.let { kotlin.math.abs(it - start - targetHeadwaySec) },
            ).sum()

        if (deviation(proposed) >= deviation(scheduledStartSec)) return null
        return RecommendationCandidate.TripShift(tripId, proposed, delta, robust.values.size)
    }
}
