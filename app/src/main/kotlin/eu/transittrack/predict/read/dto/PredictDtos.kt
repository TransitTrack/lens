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
    val rangeLowSec: Int? = null,
    val rangeHighSec: Int? = null,
    val lateProbability: Double? = null,
    val late: Boolean? = null,
    // resolver-only, not exposed as schema fields:
    val revisionId: Long,
    val gtfsFeedCode: String,
    val stopId: String,
)

data class LateAssessmentDto(
    val stopPathIndex: Int,
    val predictedArrival: String?,
    val scheduledArrival: String?,
    val rangeLowSec: Int?,
    val rangeHighSec: Int?,
    val late: Boolean?,
    val lateProbability: Double?,
    // resolver-only, not exposed as schema fields:
    val revisionId: Long,
    val gtfsFeedCode: String,
    val stopId: String,
)

data class TripLatenessDto(
    val vehicleId: String,
    val algorithm: PredictionAlgorithm,
    val nextStop: LateAssessmentDto?,
    val nextTimepoint: LateAssessmentDto?,
    val terminal: LateAssessmentDto?,
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
    val rangeCoverage: Double?,
    val rangeSampleCount: Int,
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
    val rangeCoverage: Double?,
    val rangeSampleCount: Int,
)

data class HeadwayDto(
    val stopId: String,
    val routeId: String,
    val directionId: Int?,
    val waitSec: Int?,
    val gapsSec: List<Int>,
    val scheduledHeadwaySec: Int?,
)
