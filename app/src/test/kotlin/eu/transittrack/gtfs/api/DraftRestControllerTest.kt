@file:Suppress("ktlint:standard:max-line-length")

package eu.transittrack.gtfs.api

import kotlin.test.Test

import kotlinx.serialization.json.Json
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

import eu.transittrack.gtfs.draft.DraftJobService
import eu.transittrack.gtfs.draft.DraftKind
import eu.transittrack.gtfs.draft.DraftService
import eu.transittrack.gtfs.draft.edit.DraftEditService
import eu.transittrack.gtfs.draft.edit.StaleDraftException
import eu.transittrack.gtfs.feed.GtfsFeedService
import eu.transittrack.gtfs.revision.GtfsRevision
import eu.transittrack.gtfs.revision.GtfsRevisionStatus
import eu.transittrack.rest.RestRevisionMapper

class DraftRestControllerTest {
    @Test
    fun `shift trip keeps editor and optimistic version`() {
        val edits = mock<DraftEditService>()
        val draft = GtfsRevision(
            feedId = 1, status = GtfsRevisionStatus.READY, sourceUrl = "x", kind = DraftKind.DRAFT, version = 4, id = 1,
        )
        whenever(edits.apply(eq(1), eq("alice"), eq(3), any())).thenReturn(DraftEditService.DraftEditResultData(draft, null, true, false))
        val feeds = mock<GtfsFeedService>()
        whenever(feeds.codeOf(1)).thenReturn("f")
        val mvc = MockMvcBuilders
            .standaloneSetup(
                DraftRestController(mock<DraftService>(), edits, mock<DraftJobService>(), feeds, RestRevisionMapper(), Json),
            ).build()
        mvc
            .perform(
                post("/api/v1/draft-edit-commands/shift-trip")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"draftId":"1","editor":"alice","expectedVersion":3,"tripId":"trip","deltaSec":60}"""),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.draft.version").value(4))
            .andExpect(jsonPath("$.canUndo").value(true))
    }

    @Test
    fun `stale version is reported as conflict`() {
        val edits = mock<DraftEditService>()
        whenever(edits.apply(eq(1), eq("alice"), eq(3), any())).thenThrow(StaleDraftException(4))
        val mvc = MockMvcBuilders
            .standaloneSetup(
                DraftRestController(mock<DraftService>(), edits, mock<DraftJobService>(), mock<GtfsFeedService>(), RestRevisionMapper(), Json),
            ).build()
        mvc
            .perform(
                post("/api/v1/draft-edit-commands/shift-trip")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"draftId":"1","editor":"alice","expectedVersion":3,"tripId":"trip","deltaSec":60}"""),
            ).andExpect(status().isConflict)
            .andExpect(jsonPath("$.status").value(409))
            .andExpect(jsonPath("$.code").value("STALE_DRAFT"))
            .andExpect(jsonPath("$.currentVersion").value(4))
    }
}
