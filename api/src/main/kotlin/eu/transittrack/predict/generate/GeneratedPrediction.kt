package eu.transittrack.predict.generate

import java.time.Instant

/** A single strategy's output for one stop; not implementing any shared marker interface. */
data class GeneratedPrediction(
    val stopPathIndex: Int,
    val tripRowId: Long,
    val tripPatternId: Long,
    val predictedArrivalTs: Instant?,
    val predictedDepartureTs: Instant?,
    val confidenceSec: Int?,
)
