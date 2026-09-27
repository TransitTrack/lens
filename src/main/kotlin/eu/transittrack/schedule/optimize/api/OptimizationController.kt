package eu.transittrack.schedule.optimize.api

import java.time.Instant
import java.time.format.DateTimeParseException

import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.MutationMapping
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.stereotype.Controller
import tools.jackson.databind.json.JsonMapper

import eu.transittrack.gtfs.api.DraftMapper
import eu.transittrack.gtfs.api.dto.DraftDto
import eu.transittrack.gtfs.draft.DraftService
import eu.transittrack.gtfs.feed.GtfsFeedService
import eu.transittrack.schedule.optimize.OptimizationRunRequest
import eu.transittrack.schedule.optimize.ScheduleOptimizationService
import eu.transittrack.schedule.optimize.model.OptimizationRecommendationRow
import eu.transittrack.schedule.optimize.model.OptimizationRecommendationStatus
import eu.transittrack.schedule.optimize.model.OptimizationRunRow

/**
 * GraphQL surface for the schedule-optimization workflow (design section 8). Every mutation/query
 * here is a thin adapter over [ScheduleOptimizationService] — no business logic lives in this
 * class. `applyOptimizationRecommendations` returns the plan's literal `Draft!` type (the same one
 * `drafts`/`draft` already return), mapped exactly like [eu.transittrack.gtfs.api.DraftEditController]
 * maps its own mutation results, so an applied optimization draft is indistinguishable from any
 * other draft in the review/rebuild/activate pipeline.
 */
@Controller
class OptimizationController(
    private val service: ScheduleOptimizationService,
    private val draftMapper: DraftMapper,
    private val draftService: DraftService,
    private val feedService: GtfsFeedService,
    private val json: JsonMapper,
) {
    @QueryMapping
    fun optimizationRuns(
        @Argument feedCode: String,
        @Argument limit: Int,
        @Argument offset: Int,
    ): List<OptimizationRunDto> = service.listRuns(feedCode, limit, offset).map(::toDto)

    @QueryMapping
    fun optimizationRun(
        @Argument id: String,
    ): OptimizationRunDto? = service.get(id.toLong())?.let(::toDto)

    @QueryMapping
    fun optimizationRecommendations(
        @Argument runId: String,
        @Argument status: String?,
        @Argument offset: Int,
        @Argument limit: Int,
    ): List<OptimizationRecommendationDto> =
        service
            .listRecommendations(runId.toLong(), status?.let(OptimizationRecommendationStatus::valueOf), offset, limit)
            .map(::toDto)

    @MutationMapping
    fun startOptimizationRun(
        @Argument input: OptimizationRunInput,
    ): OptimizationRunDto = toDto(service.submit(toRequest(input)))

    @MutationMapping
    fun applyOptimizationRecommendations(
        @Argument runId: String,
        @Argument recommendationIds: List<String>,
        @Argument label: String?,
        @Argument editor: String,
    ): DraftDto {
        val revision = service.apply(runId.toLong(), recommendationIds.map { it.toLong() }.toSet(), label, editor)
        val feedCode = feedService.codeOf(revision.feedId) ?: ""
        return draftMapper.toDto(revision, feedCode, draftService.currentLock(revision))
    }

    private fun toRequest(input: OptimizationRunInput): OptimizationRunRequest =
        OptimizationRunRequest(
            feedCode = input.feedCode,
            serviceId = input.serviceId,
            routeId = input.routeId,
            directionId = input.directionId,
            windowFromSec = input.windowFromSec,
            windowToSec = input.windowToSec,
            observedFrom = parseInstant(input.observedFrom, "observedFrom"),
            observedTo = parseInstant(input.observedTo, "observedTo"),
            minimumSamples = input.minimumSamples,
        )

    /** `Instant.parse` throws [DateTimeParseException], which is not one of the resolver's generically
     * mapped types — rewrap as [IllegalArgumentException] here so a malformed date surfaces as `BAD_REQUEST`
     * like every other validation failure in this mutation, rather than `INTERNAL_ERROR`. */
    private fun parseInstant(
        value: String,
        field: String,
    ): Instant =
        try {
            Instant.parse(value)
        } catch (e: DateTimeParseException) {
            throw IllegalArgumentException("$field is not a valid ISO-8601 instant: '$value'", e)
        }

    private fun toDto(run: OptimizationRunRow): OptimizationRunDto =
        OptimizationRunDto(
            id = run.id.toString(),
            revisionId = run.revisionId.toString(),
            state = run.state.name,
            error = run.error,
            createdAt = run.createdAt.toString(),
            completedAt = run.completedAt?.toString(),
        )

    private fun toDto(row: OptimizationRecommendationRow): OptimizationRecommendationDto =
        OptimizationRecommendationDto(
            id = row.id.toString(),
            kind = row.kind.name,
            status = row.status.name,
            sampleCount = row.sampleCount,
            deltaSec = row.deltaSec,
            currentValue = jsonValue(row.currentValue),
            proposedValue = jsonValue(row.proposedValue),
            evidence = jsonValue(row.evidence),
            reason = row.reason,
        )

    /** Stored JSONB is a JSON string here, but the schema's `JSON!` scalar is non-null — a genuinely
     * null column (never produced by the analysis pipeline today) maps to an empty object rather than
     * violating non-null. */
    private fun jsonValue(raw: String?): Any = raw?.let { json.readValue(it, Map::class.java) } ?: emptyMap<String, Any>()
}
