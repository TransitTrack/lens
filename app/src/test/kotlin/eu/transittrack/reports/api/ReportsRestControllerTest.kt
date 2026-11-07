package eu.transittrack.reports.api

import java.time.LocalDate
import kotlin.test.Test

import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

import eu.transittrack.reports.model.RouteAdherenceSummary
import eu.transittrack.reports.model.StopPassage
import eu.transittrack.reports.read.AdherenceReadService

class ReportsRestControllerTest {
    @Test
    fun `routes report filters and paginates through generated REST interface`() {
        val read = mock<AdherenceReadService>()
        whenever(read.allRoutesAdherence("avl", "2026-01-01", "2026-01-02"))
            .thenReturn(listOf(RouteAdherenceSummary("1", "A", 4, 2.0, 0.5), RouteAdherenceSummary("2", "B", 2, 1.0, 1.0)))
        val mvc = MockMvcBuilders.standaloneSetup(ReportsRestController(read)).build()
        mvc
            .perform(
                get("/api/v1/avl-feeds/avl/reports/route-adherence")
                    .param("startDate", "2026-01-01")
                    .param("endDate", "2026-01-02")
                    .param("offset", "1")
                    .param("limit", "1"),
            ).andExpect(status().isOk)
            .andExpect(header().string("X-Total-Count", "2"))
            .andExpect(jsonPath("$[0].routeId").value("2"))
            .andExpect(jsonPath("$[1]").doesNotExist())
    }

    @Test
    fun `invalid date range returns a problem response`() {
        val mvc = MockMvcBuilders.standaloneSetup(ReportsRestController(mock<AdherenceReadService>())).build()
        mvc
            .perform(
                get("/api/v1/avl-feeds/avl/reports/route-adherence")
                    .param("startDate", "2026-01-03")
                    .param("endDate", "2026-01-02"),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.status").value(400))
    }

    @Test
    fun `stop report maps service dates and nullable observations`() {
        val read = mock<AdherenceReadService>()
        val passage = StopPassage("trip", "route", null, LocalDate.of(2026, 1, 1), "stop", null, 2, 100, null, null, "bus", null, null)
        whenever(read.stopAdherence("avl", "stop", "2026-01-01", "2026-01-02")).thenReturn(listOf(passage))
        val mvc = MockMvcBuilders.standaloneSetup(ReportsRestController(read)).build()
        mvc
            .perform(
                get("/api/v1/avl-feeds/avl/stops/stop/adherence")
                    .param("startDate", "2026-01-01")
                    .param("endDate", "2026-01-02"),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$[0].serviceDate").value("2026-01-01"))
            .andExpect(jsonPath("$[0].scheduledSec").value(100))
            .andExpect(header().string("X-Total-Count", "1"))
    }

    @Test
    fun `invalid pagination is rejected`() {
        val mvc = MockMvcBuilders.standaloneSetup(ReportsRestController(mock<AdherenceReadService>())).build()
        mvc
            .perform(
                get("/api/v1/avl-feeds/avl/reports/route-adherence")
                    .param("startDate", "2026-01-01")
                    .param("endDate", "2026-01-02")
                    .param("limit", "501"),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.status").value(400))
    }
}
