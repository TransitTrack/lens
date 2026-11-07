package eu.transittrack.schedule.api

import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.GZIPOutputStream
import kotlin.test.Test

import kotlinx.serialization.json.Json
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

import eu.transittrack.schedule.compare.ScheduleComparison
import eu.transittrack.schedule.compare.ScheduleComparisonService
import eu.transittrack.schedule.compare.TripChange
import eu.transittrack.schedule.compare.TripChangeKind
import eu.transittrack.schedule.read.FeedGeometryService
import eu.transittrack.schedule.read.ScheduleReadService

class ScheduleRestControllerTest {
    @Test
    fun `comparison maps service data and pages trip changes`() {
        val comparisons = mock<ScheduleComparisonService>()
        val changes = listOf(
            TripChange("first", TripChangeKind.MODIFIED, mapOf("headsign" to ("old" to "new")), emptyList(), 20),
            TripChange("second", TripChangeKind.ADDED, emptyMap(), emptyList(), null),
        )
        whenever(comparisons.compare(1, 2, null, null, null))
            .thenReturn(ScheduleComparison(1, 2, false, true, changes, emptyList(), emptyList()))
        val mvc = MockMvcBuilders
            .standaloneSetup(
                ScheduleRestController(mock(), mock(), comparisons, Json, mock()),
            ).build()
        mvc
            .perform(get("/api/v1/revision-comparisons").param("fromRevisionId", "1").param("toRevisionId", "2").param("limit", "1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.tripChangeCount").value(2))
            .andExpect(jsonPath("$.tripChanges[0].fieldChanges.headsign.from").value("old"))
            .andExpect(jsonPath("$.tripChanges[1]").doesNotExist())
    }

    @Test
    fun `geometry is exposed as GeoJSON instead of base64`() {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use {
            it.write(
                """{"type":"FeatureCollection","features":[{"type":"Feature","geometry":{"type":"Point","coordinates":[26,44]},"properties":{"kind":"stop","name":"Station"}}]}"""
                    .toByteArray(),
            )
        }
        val service = mock<FeedGeometryService>()
        whenever(service.feedGeometry("f", null)).thenReturn(Base64.getEncoder().encodeToString(out.toByteArray()))
        val mvc = MockMvcBuilders
            .standaloneSetup(
                ScheduleRestController(
                    mock<ScheduleReadService>(), service, mock<ScheduleComparisonService>(), Json,
                    mock(),
                ),
            ).build()
        mvc
            .perform(get("/api/v1/feeds/f/geometry"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.type").value("FeatureCollection"))
            .andExpect(jsonPath("$.features[0].geometry.type").value("Point"))
            .andExpect(jsonPath("$.features[0].geometry.coordinates[0]").value(26))
            .andExpect(jsonPath("$.features[0].properties.name").value("Station"))
    }
}
