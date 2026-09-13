package eu.transittrack.schedule.optimize.api

/** GraphQL `OptimizationRunInput`. `observedFrom`/`observedTo` are ISO-8601 strings, parsed at the controller boundary. */
data class OptimizationRunInput(
    val feedCode: String,
    val serviceId: String?,
    val routeId: String?,
    val directionId: Int?,
    val windowFromSec: Int?,
    val windowToSec: Int?,
    val observedFrom: String,
    val observedTo: String,
    val minimumSamples: Int,
)

/** GraphQL `OptimizationRun`. */
data class OptimizationRunDto(
    val id: String,
    val revisionId: String,
    val state: String,
    val error: String?,
    val createdAt: String,
    val completedAt: String?,
)

/**
 * GraphQL `OptimizationRecommendation`. `currentValue`/`proposedValue`/`evidence` are the entity's
 * stored JSONB strings parsed into plain `Map`/`List` structures for the `JSON!` scalar — never the
 * raw JPA row.
 */
data class OptimizationRecommendationDto(
    val id: String,
    val kind: String,
    val status: String,
    val sampleCount: Int,
    val deltaSec: Int,
    val currentValue: Any,
    val proposedValue: Any,
    val evidence: Any,
    val reason: String,
)
