package eu.transittrack.predict.read.dto

import eu.transittrack.predict.PredictionAlgorithm

data class StopPredictionDto(
    val stopPathIndex: Int,
    val scheduledArrival: String?,
    val scheduledDeparture: String?,
    val actualArrival: String?,
    val actualDeparture: String?,
    val predictedArrival: String?,
    val predictedDeparture: String?,
    val algorithm: PredictionAlgorithm?,
    val confidenceSec: Int?,
    // resolver-only, not exposed as schema fields:
    val revisionId: Long,
    val gtfsFeedCode: String,
    val stopId: String,
)

data class PredictionAccuracySummaryDto(
    val algorithm: PredictionAlgorithm,
    val sampleCount: Int,
    val meanErrorSec: Double,
    val meanAbsErrorSec: Double,
    val byHorizon: List<PredictionAccuracyHorizonDto> = emptyList(),
)

data class PredictionAccuracyHorizonDto(
    val horizonSec: Int,
    val sampleCount: Int,
    val meanErrorSec: Double,
    val meanAbsErrorSec: Double,
)

data class HeadwayDto(
    val stopId: String,
    val routeId: String,
    val directionId: Int?,
    val waitSec: Int?,
    val gapsSec: List<Int>,
    val scheduledHeadwaySec: Int?,
)
