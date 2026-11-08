package eu.transittrack.gtfs.api

import java.time.Instant
import kotlin.test.Test

import kotlinx.serialization.json.Json
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

import eu.transittrack.gtfs.draft.DraftJobService
import eu.transittrack.gtfs.draft.DraftService
import eu.transittrack.gtfs.draft.edit.DraftEditService
import eu.transittrack.gtfs.feed.FeedSource
import eu.transittrack.gtfs.feed.GtfsFeed
import eu.transittrack.gtfs.feed.GtfsFeedService
import eu.transittrack.gtfs.model.Route
import eu.transittrack.gtfs.model.Stop
import eu.transittrack.gtfs.read.GtfsReadService
import eu.transittrack.gtfs.read.RevisionResolver
import eu.transittrack.gtfs.revision.GtfsRevision
import eu.transittrack.gtfs.revision.GtfsRevisionStatus
import eu.transittrack.gtfs.revision.RevisionService
import eu.transittrack.rest.RestDraftMapper
import eu.transittrack.rest.RestRevisionMapper
import eu.transittrack.rest.testEntityMapper

class GtfsRestControllerTest {
    @Test
    fun `loads a requested stop directly into the generated REST model`() {
        val feeds = mock<GtfsFeedService>()
        val revisions = mock<RevisionService>()
        val read = mock<GtfsReadService>()
        whenever(read.stop("f", "stop", null)).thenReturn(
            Stop(
                revisionId = 42, stopId = "stop", stopCode = null, stopName = "Station", ttsStopName = null,
                stopDesc = null, stopLat = null, stopLon = null, zoneId = null, stopUrl = null,
                locationType = null, parentStation = null, stopTimezone = null, wheelchairBoarding = null,
                levelId = null, platformCode = null,
            ),
        )
        val mvc = MockMvcBuilders
            .standaloneSetup(
                GtfsRestController(
                    read, mock(), mock(), feeds, revisions, RestRevisionMapper(), mock(), mock(),
                    mock(), mock(), mock(), RestDraftMapper(mock(), feeds, Json), mock(), testEntityMapper(),
                ),
            ).build()
        mvc
            .perform(get("/api/v1/feeds/f/stops/stop"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.stopName").value("Station"))
    }

    @Test
    fun `gets a revision directly from revision service`() {
        val feeds = mock<GtfsFeedService>()
        val revisions = mock<RevisionService>()
        whenever(feeds.codeOf(42)).thenReturn("f")
        whenever(revisions.findOrNull(1)).thenReturn(
            GtfsRevision(feedId = 42, status = GtfsRevisionStatus.ACTIVE, sourceUrl = "x", id = 1),
        )
        val mvc = MockMvcBuilders
            .standaloneSetup(
                GtfsRestController(
                    mock(), mock(), mock(), feeds, revisions, RestRevisionMapper(), mock(),
                    mock<RevisionResolver>(), mock<DraftService>(), mock<DraftEditService>(), mock<DraftJobService>(),
                    RestDraftMapper(mock(), feeds, Json), mock(), testEntityMapper(),
                ),
            ).build()
        mvc
            .perform(get("/api/v1/revisions/1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value("1"))
            .andExpect(jsonPath("$.feedCode").value("f"))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
    }

    private fun feed() =
        GtfsFeed(
            "f", "Feed", null, "https://example.org", null, true, null, FeedSource.CONFIG,
            Instant.EPOCH, Instant.EPOCH, id = 42,
        )

    @Test
    fun `lists feeds with the generated model and page count`() {
        val feeds = mock<GtfsFeedService>()
        val revisions = mock<RevisionService>()
        whenever(feeds.list()).thenReturn(listOf(feed()))
        val mvc = MockMvcBuilders
            .standaloneSetup(
                GtfsRestController(
                    mock<GtfsReadService>(), mock(), mock(), feeds, revisions, RestRevisionMapper(), mock(),
                    mock<RevisionResolver>(), mock<DraftService>(), mock<DraftEditService>(), mock<DraftJobService>(),
                    RestDraftMapper(mock(), feeds, Json), mock(), testEntityMapper(),
                ),
            ).build()
        mvc
            .perform(get("/api/v1/feeds"))
            .andExpect(status().isOk)
            .andExpect(header().string("X-Total-Count", "1"))
            .andExpect(jsonPath("$[0].code").value("f"))
            .andExpect(jsonPath("$[0].feedId").doesNotExist())
    }

    @Test
    fun `stopId and routeId filters bind every repeated value`() {
        val feeds = mock<GtfsFeedService>()
        val read = mock<GtfsReadService>()
        whenever(read.stops("f", null, null, listOf("a", "b"))).thenReturn(listOf(stop("a"), stop("b")))
        whenever(read.routes("f", null, listOf("r1"))).thenReturn(listOf(route("r1")))
        val mvc = MockMvcBuilders
            .standaloneSetup(
                GtfsRestController(
                    read, mock(), mock(), feeds, mock(), RestRevisionMapper(), mock(), mock(),
                    mock(), mock(), mock(), RestDraftMapper(mock(), feeds, Json), mock(), testEntityMapper(),
                ),
            ).build()

        mvc
            .perform(get("/api/v1/feeds/f/stops").param("stopId", "a", "b"))
            .andExpect(status().isOk)
            .andExpect(header().string("X-Total-Count", "2"))
            .andExpect(jsonPath("$[1].stopId").value("b"))
        mvc
            .perform(get("/api/v1/feeds/f/routes").param("routeId", "r1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].routeId").value("r1"))
    }

    private fun stop(id: String) =
        Stop(
            revisionId = 42, stopId = id, stopCode = null, stopName = id, ttsStopName = null,
            stopDesc = null, stopLat = null, stopLon = null, zoneId = null, stopUrl = null,
            locationType = null, parentStation = null, stopTimezone = null, wheelchairBoarding = null,
            levelId = null, platformCode = null,
        )

    private fun route(id: String) =
        Route(
            revisionId = 42, routeId = id, agencyId = null, routeShortName = id, routeLongName = null,
            routeDesc = null, routeType = 3, routeUrl = null, routeColor = null, routeTextColor = null,
            routeSortOrder = null, continuousPickup = null, continuousDropOff = null, networkId = null,
        )
}
