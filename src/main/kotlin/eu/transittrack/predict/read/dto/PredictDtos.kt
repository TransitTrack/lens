package eu.transittrack.predict.read.dto

data class StopPredictionDto(
    val stopPathIndex: Int,
    val scheduledArrival: String?,
    val scheduledDeparture: String?,
    val actualArrival: String?,
    val actualDeparture: String?,
    val predictedArrival: String?,
    val predictedDeparture: String?,
    val algorithm: String?,
    val confidenceSec: Int?,
    // resolver-only, not exposed as schema fields:
    val revisionId: Long,
    val gtfsFeedCode: String,
    val stopId: String,
)

data class PredictionAccuracySummaryDto(
    val algorithm: String,
    val sampleCount: Int,
    val meanErrorSec: Double,
    val meanAbsErrorSec: Double,
)
