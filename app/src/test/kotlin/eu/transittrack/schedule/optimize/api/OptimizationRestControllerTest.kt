package eu.transittrack.schedule.optimize.api

import java.time.Instant
import kotlin.test.Test

import kotlinx.serialization.json.Json
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

import eu.transittrack.gtfs.draft.DraftService
import eu.transittrack.gtfs.feed.GtfsFeedService
import eu.transittrack.schedule.optimize.RecommendationConflictException
import eu.transittrack.schedule.optimize.ScheduleOptimizationService
import eu.transittrack.schedule.optimize.model.OptimizationRunRow
import eu.transittrack.schedule.optimize.model.OptimizationRunState

class OptimizationRestControllerTest {
    @Test
    fun `gets an optimization run using the generated REST contract`() {
        val service = mock<ScheduleOptimizationService>()
        val now = Instant.parse("2026-01-01T00:00:00Z")
        whenever(service.get(42)).thenReturn(
            OptimizationRunRow(
                "feed", 7, observedFrom = now, observedTo = now, minimumSamples = 1,
                state = OptimizationRunState.QUEUED, createdAt = now, id = 42,
            ),
        )
        val mvc = MockMvcBuilders
            .standaloneSetup(OptimizationRestController(service, mock<DraftService>(), mock<GtfsFeedService>(), Json))
            .build()
        mvc
            .perform(get("/api/v1/optimization-runs/42"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value("42"))
            .andExpect(jsonPath("$.revisionId").value("7"))
    }

    @Test
    fun `conflicting recommendations return identifiers`() {
        val service = mock<ScheduleOptimizationService>()
        whenever(service.apply(42, setOf(5L, 6L), null, "alice"))
            .thenThrow(RecommendationConflictException(setOf(5L, 6L)))
        val mvc = MockMvcBuilders
            .standaloneSetup(OptimizationRestController(service, mock<DraftService>(), mock<GtfsFeedService>(), Json))
            .build()
        mvc
            .perform(
                post("/api/v1/optimization-runs/42/recommendation-applications")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"recommendationIds":["5","6"],"editor":"alice"}"""),
            ).andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("RECOMMENDATION_CONFLICT"))
            .andExpect(jsonPath("$.conflictingRecommendationIds[0]").value("5"))
    }
}
