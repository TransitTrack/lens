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
    val sourceReportTs: String? = null,
    val actualReportGapSec: Int? = null,
    val actualReliable: Boolean? = null,
    // resolver-only, not exposed as schema fields:
    val revisionId: Long,
    val gtfsFeedCode: String,
    val stopId: String,
)

data class PredictionAccuracySummaryDto(
    val algorithm: PredictionAlgorithm,
    val sampleCount: Int,
    val meanErrorSec: Double?,
    val meanAbsErrorSec: Double?,
    val eligibleCount: Int,
    val predictionCoverage: Double,
    val pairedSampleCount: Int,
    val p90AbsErrorSec: Double?,
    val p95AbsErrorSec: Double?,
    val confidenceCoverage: Double?,
    val confidenceSampleCount: Int,
    val byHorizon: List<PredictionAccuracyHorizonDto> = emptyList(),
)

data class PredictionAccuracyHorizonDto(
    val horizonSec: Int,
    val sampleCount: Int,
    val meanErrorSec: Double?,
    val meanAbsErrorSec: Double?,
    val eligibleCount: Int,
    val predictionCoverage: Double,
    val pairedSampleCount: Int,
    val p90AbsErrorSec: Double?,
    val p95AbsErrorSec: Double?,
    val confidenceCoverage: Double?,
    val confidenceSampleCount: Int,
)

data class HeadwayDto(
    val stopId: String,
    val routeId: String,
    val directionId: Int?,
    val waitSec: Int?,
    val gapsSec: List<Int>,
    val scheduledHeadwaySec: Int?,
)
