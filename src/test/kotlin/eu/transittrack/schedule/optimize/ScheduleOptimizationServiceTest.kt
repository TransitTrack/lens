package eu.transittrack.schedule.optimize

import java.time.Instant
import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

import eu.transittrack.gtfs.feed.FeedSource
import eu.transittrack.gtfs.feed.GtfsFeed
import eu.transittrack.gtfs.feed.GtfsFeedRepository
import eu.transittrack.gtfs.revision.GtfsRevision
import eu.transittrack.gtfs.revision.GtfsRevisionRepository
import eu.transittrack.gtfs.revision.GtfsRevisionStatus

class ScheduleOptimizationServiceTest {
    @Test
    fun `submits a queued run against the feed active revision`() {
        val feeds = mock<GtfsFeedRepository>()
        val revisions = mock<GtfsRevisionRepository>()
        val runs = mock<OptimizationRunRepository>()
        val feed = GtfsFeed("g", "G", null, "x", null, true, null, FeedSource.API, Instant.EPOCH, Instant.EPOCH, id = 7)
        val revision = GtfsRevision(7, GtfsRevisionStatus.ACTIVE, "x", id = 11)
        whenever(feeds.findByCode("g")).thenReturn(feed)
        whenever(revisions.findByFeedAndStatus(7, GtfsRevisionStatus.ACTIVE)).thenReturn(revision)
        whenever(runs.save(any<OptimizationRunRow>())).thenAnswer { it.arguments[0] }

        val result = ScheduleOptimizationService(feeds, revisions, runs).submit(
            OptimizationRunRequest("g", Instant.parse("2026-09-01T00:00:00Z"), Instant.parse("2026-09-08T00:00:00Z"), 20),
        )

        assertThat(result.revisionId).isEqualTo(11)
        assertThat(result.state).isEqualTo(OptimizationRunState.QUEUED)
    }
}
