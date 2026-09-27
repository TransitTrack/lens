package eu.transittrack.schedule.optimize.api

import java.time.Instant
import kotlin.test.Test

import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.graphql.test.autoconfigure.GraphQlTest
import org.springframework.context.annotation.Import
import org.springframework.graphql.test.tester.GraphQlTester
import org.springframework.test.context.bean.override.mockito.MockitoBean
import tools.jackson.databind.json.JsonMapper

import eu.transittrack.gtfs.api.DraftMapper
import eu.transittrack.gtfs.api.GraphQlConfiguration
import eu.transittrack.gtfs.draft.DraftLock
import eu.transittrack.gtfs.draft.DraftService
import eu.transittrack.gtfs.feed.GtfsFeedService
import eu.transittrack.gtfs.revision.GtfsRevision
import eu.transittrack.gtfs.revision.GtfsRevisionStatus
import eu.transittrack.schedule.optimize.RecommendationConflictException
import eu.transittrack.schedule.optimize.ScheduleOptimizationService
import eu.transittrack.schedule.optimize.model.OptimizationRecommendationKind
import eu.transittrack.schedule.optimize.model.OptimizationRecommendationRow
import eu.transittrack.schedule.optimize.model.OptimizationRecommendationStatus
import eu.transittrack.schedule.optimize.model.OptimizationRunRow
import eu.transittrack.schedule.optimize.model.OptimizationRunState

/**
 * Slice test for [OptimizationController] (design section 8), following
 * [eu.transittrack.gtfs.api.DraftEditGraphQlTest]'s pattern: a mocked [ScheduleOptimizationService]
 * so the controller's argument parsing/DTO mapping/exception mapping is exercised without a real
 * database.
 */
@GraphQlTest(controllers = [OptimizationController::class])
@Import(GraphQlConfiguration::class, DraftMapper::class)
class OptimizationGraphQlTest(
    @Autowired val tester: GraphQlTester,
) {
    @MockitoBean lateinit var service: ScheduleOptimizationService

    @MockitoBean lateinit var draftService: DraftService

    @MockitoBean lateinit var feedService: GtfsFeedService

    private val json = JsonMapper.builder().build()

    private fun run(
        id: Long = 1,
        revisionId: Long = 11,
        state: OptimizationRunState = OptimizationRunState.QUEUED,
    ) = OptimizationRunRow(
        feedId = 5,
        revisionId = revisionId,
        observedFrom = Instant.parse("2026-09-01T00:00:00Z"),
        observedTo = Instant.parse("2026-09-08T00:00:00Z"),
        minimumSamples = 5,
        state = state,
        createdAt = Instant.parse("2026-09-01T00:00:00Z"),
    ).apply { this.id = id }

    private fun recommendation(
        id: Long = 1,
        status: OptimizationRecommendationStatus = OptimizationRecommendationStatus.PENDING,
    ) = OptimizationRecommendationRow(
        runId = 1,
        kind = OptimizationRecommendationKind.STOP_TIME,
        sampleCount = 5,
        deltaSec = 45,
        reason = "test reason",
        status = status,
        currentValue = json.writeValueAsString(linkedMapOf("targets" to listOf(1, 2))),
        proposedValue = json.writeValueAsString(linkedMapOf("targets" to listOf(3, 4))),
        evidence = json.writeValueAsString(linkedMapOf("sampleCount" to 5)),
        conflictKey = "cell:T1:2",
    ).apply { this.id = id }

    private fun draft(id: Long = 20) =
        GtfsRevision(
            feedId = 5,
            status = GtfsRevisionStatus.DRAFT,
            sourceUrl = "x",
            baseRevisionId = 11,
            version = 1,
        ).apply { this.id = id }

    @Test
    fun `startOptimizationRun rejects a malformed observedFrom as BAD_REQUEST`() {
        tester
            .document(
                """
                mutation {
                  startOptimizationRun(input: {
                    feedCode: "g", observedFrom: "not-a-date", observedTo: "2026-09-08T00:00:00Z", minimumSamples: 5
                  }) { id }
                }
                """.trimIndent(),
            ).execute()
            .errors()
            .satisfy { errors -> assert(errors.isNotEmpty()) { "expected an error for a malformed observedFrom" } }
    }

    @Test
    fun `startOptimizationRun rejects a nonpositive minimumSamples as BAD_REQUEST`() {
        whenever(service.submit(any())).thenThrow(IllegalArgumentException("minimumSamples must be positive"))
        tester
            .document(
                """
                mutation {
                  startOptimizationRun(input: {
                    feedCode: "g", observedFrom: "2026-09-01T00:00:00Z", observedTo: "2026-09-08T00:00:00Z", minimumSamples: 0
                  }) { id }
                }
                """.trimIndent(),
            ).execute()
            .errors()
            .satisfy { errors -> assert(errors.isNotEmpty()) { "expected an error for a nonpositive minimumSamples" } }
    }

    @Test
    fun `startOptimizationRun rejects a malformed window as BAD_REQUEST`() {
        whenever(service.submit(any())).thenThrow(IllegalArgumentException("windowFromSec must be before windowToSec"))
        tester
            .document(
                """
                mutation {
                  startOptimizationRun(input: {
                    feedCode: "g", observedFrom: "2026-09-01T00:00:00Z", observedTo: "2026-09-08T00:00:00Z",
                    minimumSamples: 5, windowFromSec: 3600, windowToSec: 1800
                  }) { id }
                }
                """.trimIndent(),
            ).execute()
            .errors()
            .satisfy { errors -> assert(errors.isNotEmpty()) { "expected an error for an inverted window" } }
    }

    @Test
    fun `startOptimizationRun returns a queued run`() {
        whenever(service.submit(any())).thenReturn(run())
        tester
            .document(
                """
                mutation {
                  startOptimizationRun(input: {
                    feedCode: "g", observedFrom: "2026-09-01T00:00:00Z", observedTo: "2026-09-08T00:00:00Z", minimumSamples: 5
                  }) { id revisionId state }
                }
                """.trimIndent(),
            ).execute()
            .path("startOptimizationRun.id")
            .entity(String::class.java)
            .isEqualTo("1")
            .path("startOptimizationRun.state")
            .entity(String::class.java)
            .isEqualTo("QUEUED")
    }

    @Test
    fun `optimizationRun polls by id`() {
        whenever(service.get(1)).thenReturn(run(state = OptimizationRunState.SUCCEEDED))
        tester
            .document("""{ optimizationRun(id: "1") { state } }""")
            .execute()
            .path("optimizationRun.state")
            .entity(String::class.java)
            .isEqualTo("SUCCEEDED")
    }

    @Test
    fun `optimizationRecommendations honors status filtering and pagination`() {
        whenever(service.listRecommendations(1, OptimizationRecommendationStatus.PENDING, 10, 25))
            .thenReturn(listOf(recommendation()))
        tester
            .document(
                """{ optimizationRecommendations(runId: "1", status: "PENDING", offset: 10, limit: 25) { id kind status deltaSec } }""",
            ).execute()
            .path("optimizationRecommendations[0].id")
            .entity(String::class.java)
            .isEqualTo("1")
            .path("optimizationRecommendations[0].status")
            .entity(String::class.java)
            .isEqualTo("PENDING")

        org.mockito.kotlin
            .verify(service)
            .listRecommendations(1, OptimizationRecommendationStatus.PENDING, 10, 25)
    }

    @Test
    fun `optimizationRecommendations defaults offset and limit`() {
        whenever(service.listRecommendations(eq(1L), isNull(), eq(0), eq(100))).thenReturn(emptyList())
        tester
            .document("""{ optimizationRecommendations(runId: "1") { id } }""")
            .execute()
            .path("optimizationRecommendations")
            .entityList(Any::class.java)
            .hasSize(0)
    }

    @Test
    fun `applyOptimizationRecommendations returns a DRAFT`() {
        whenever(service.apply(eq(1L), argThat { this == setOf(2L, 3L) }, eq("proposal"), eq("planner")))
            .thenReturn(draft())
        whenever(feedService.codeOf(5)).thenReturn("g")
        whenever(draftService.currentLock(any())).thenReturn(DraftLock("planner", Instant.parse("2026-09-01T01:00:00Z")))

        tester
            .document(
                """
                mutation {
                  applyOptimizationRecommendations(runId: "1", recommendationIds: ["2", "3"], label: "proposal", editor: "planner") {
                    id status baseRevisionId
                  }
                }
                """.trimIndent(),
            ).execute()
            .path("applyOptimizationRecommendations.status")
            .entity(String::class.java)
            .isEqualTo("DRAFT")
            .path("applyOptimizationRecommendations.baseRevisionId")
            .entity(String::class.java)
            .isEqualTo("11")
    }

    @Test
    fun `applyOptimizationRecommendations surfaces a conflict as RECOMMENDATION_CONFLICT`() {
        whenever(service.apply(eq(1L), eq(setOf(2L, 3L)), isNull(), eq("planner")))
            .thenThrow(RecommendationConflictException(setOf(2L, 3L)))
        tester
            .document(
                """mutation { applyOptimizationRecommendations(runId: "1", recommendationIds: ["2", "3"], editor: "planner") { id } }""",
            ).execute()
            .errors()
            .satisfy { errors ->
                val ext = errors[0].extensions
                assert(ext["code"] == "RECOMMENDATION_CONFLICT") { "code was ${ext["code"]}" }
                assert(ext["conflictingRecommendationIds"] == listOf(2, 3) || ext["conflictingRecommendationIds"] == listOf(2L, 3L)) {
                    "ids were ${ext["conflictingRecommendationIds"]}"
                }
            }
    }
}
