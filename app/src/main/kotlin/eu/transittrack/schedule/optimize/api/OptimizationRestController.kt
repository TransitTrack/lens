package eu.transittrack.schedule.optimize.api

import java.time.Instant
import java.time.format.DateTimeParseException

import kotlinx.serialization.json.Json
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

import eu.transittrack.ConditionalOnRole
import eu.transittrack.Role
import eu.transittrack.gtfs.draft.DraftService
import eu.transittrack.gtfs.feed.GtfsFeedService
import eu.transittrack.gtfs.revision.GtfsRevision
import eu.transittrack.rest.generated.api.OptimizationApi
import eu.transittrack.rest.generated.model.ApplyOptimizationRecommendationsRequest
import eu.transittrack.rest.generated.model.Draft
import eu.transittrack.rest.generated.model.DraftKind
import eu.transittrack.rest.generated.model.DraftLock
import eu.transittrack.rest.generated.model.OptimizationRecommendation
import eu.transittrack.rest.generated.model.OptimizationRecommendationKind
import eu.transittrack.rest.generated.model.OptimizationRecommendationStatus
import eu.transittrack.rest.generated.model.OptimizationRun
import eu.transittrack.rest.generated.model.OptimizationRunInput
import eu.transittrack.rest.generated.model.OptimizationRunState
import eu.transittrack.rest.generated.model.Problem
import eu.transittrack.rest.generated.model.RevisionStatus
import eu.transittrack.schedule.optimize.OptimizationRunRequest
import eu.transittrack.schedule.optimize.RecommendationConflictException
import eu.transittrack.schedule.optimize.ScheduleOptimizationService
import eu.transittrack.schedule.optimize.model.OptimizationRunRow
import eu.transittrack.toScalarValue

@RestController
@RequestMapping("/api/v1")
@ConditionalOnRole(Role.API)
class OptimizationRestController(
    private val service: ScheduleOptimizationService,
    private val draftService: DraftService,
    private val feedService: GtfsFeedService,
    private val json: Json,
) : OptimizationApi {
    @ExceptionHandler(ResponseStatusException::class)
    fun error(ex: ResponseStatusException): ResponseEntity<Problem> =
        ResponseEntity
            .status(ex.statusCode)
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(
                Problem(
                    title = if (ex.statusCode.value() ==
                        404
                    ) {
                        "Not Found"
                    } else {
                        "Bad Request"
                    },
                    status = ex.statusCode.value(), detail = ex.reason,
                ),
            )

    @ExceptionHandler(RecommendationConflictException::class)
    fun conflict(ex: RecommendationConflictException): ResponseEntity<Problem> =
        ResponseEntity
            .status(HttpStatus.CONFLICT)
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(
                Problem(
                    title = "Conflict", status = 409, detail = ex.message, code = "RECOMMENDATION_CONFLICT",
                    conflictingRecommendationIds = ex.conflictingRecommendationIds.sorted().map(Long::toString),
                ),
            )

    @ExceptionHandler(IllegalArgumentException::class)
    fun invalid(ex: IllegalArgumentException): ResponseEntity<Problem> =
        ResponseEntity
            .badRequest()
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(Problem(title = "Bad Request", status = 400, detail = ex.message))

    override fun optimizationRuns(
        feedCode: String,
        limit: Int,
        offset: Int,
    ): ResponseEntity<List<OptimizationRun>> {
        if (limit < 1 || offset < 0) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid pagination")
        return ResponseEntity.ok(service.listRuns(feedCode, limit, offset).map(::run))
    }

    override fun optimizationRun(id: String): ResponseEntity<OptimizationRun> {
        val value = service.get(id.toLong()) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Optimization run not found")
        return ResponseEntity.ok(run(value))
    }

    override fun optimizationRecommendations(
        runId: String,
        status: OptimizationRecommendationStatus?,
        offset: Int,
        limit: Int,
    ): ResponseEntity<List<OptimizationRecommendation>> {
        if (limit < 1 || offset < 0) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid pagination")
        val state = status?.let {
            eu.transittrack.schedule.optimize.model.OptimizationRecommendationStatus
                .valueOf(it.name)
        }
        return ResponseEntity.ok(
            service.listRecommendations(runId.toLong(), state, offset, limit).map { r ->
                OptimizationRecommendation(
                    r.id.toString(), OptimizationRecommendationKind.valueOf(r.kind.name),
                    OptimizationRecommendationStatus
                        .valueOf(r.status.name),
                    r.sampleCount, r.deltaSec, jsonValue(r.currentValue), jsonValue(r.proposedValue), jsonValue(r.evidence), r.reason,
                )
            },
        )
    }

    override fun startOptimizationRun(optimizationRunInput: OptimizationRunInput): ResponseEntity<OptimizationRun> {
        val i = optimizationRunInput
        return ResponseEntity.ok(
            run(
                service.submit(
                    OptimizationRunRequest(
                        feedCode = i.feedCode, serviceId = i.serviceId, routeId = i.routeId,
                        directionId = i.directionId, windowFromSec = i.windowFromSec, windowToSec = i.windowToSec,
                        observedFrom = parseInstant(i.observedFrom, "observedFrom"),
                        observedTo = parseInstant(i.observedTo, "observedTo"), minimumSamples = i.minimumSamples,
                    ),
                ),
            ),
        )
    }

    override fun applyOptimizationRecommendations(
        runId: String,
        applyOptimizationRecommendationsRequest: ApplyOptimizationRecommendationsRequest,
    ): ResponseEntity<Draft> {
        val i = applyOptimizationRecommendationsRequest
        val revision = service.apply(runId.toLong(), i.recommendationIds.map(String::toLong).toSet(), i.label, i.editor)
        return ResponseEntity.ok(draft(revision))
    }

    private fun run(x: OptimizationRunRow) =
        OptimizationRun(
            x.id.toString(), x.revisionId.toString(), OptimizationRunState.valueOf(x.state.name),
            x.createdAt.toString(), x.error, x.completedAt?.toString(),
        )

    private fun draft(d: GtfsRevision) =
        Draft(
            id = d.id.toString(), feedCode = feedService.codeOf(d.feedId) ?: "",
            status = RevisionStatus.valueOf(d.status.name), kind = DraftKind.valueOf(d.kind.name),
            version = d.version, derivationStale = d.derivationStale, createdAt = d.createdAt.toString(), label = d.label,
            baseRevisionId = d.baseRevisionId?.toString(),
            lastValidation = d.lastValidation?.let { json.parseToJsonElement(it).toScalarValue() },
            lock = draftService.currentLock(d)?.let { DraftLock(it.editor, it.expiresAt.toString()) },
            createdBy = d.createdBy, rowCounts = d.rowCounts,
        )

    private fun jsonValue(raw: String?): Any = raw?.let { json.parseToJsonElement(it).toScalarValue() } ?: emptyMap<String, Any>()

    private fun parseInstant(
        value: String,
        field: String,
    ): Instant =
        try {
            Instant.parse(value)
        } catch (e: DateTimeParseException) {
            throw IllegalArgumentException("$field is not a valid ISO-8601 instant: '$value'", e)
        }
}
