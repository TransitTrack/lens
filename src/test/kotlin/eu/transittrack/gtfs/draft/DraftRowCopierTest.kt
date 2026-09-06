package eu.transittrack.gtfs.draft

import java.time.Instant
import javax.sql.DataSource
import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.doesNotContain
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNotNull
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate

import eu.transittrack.gtfs.feed.FeedSource
import eu.transittrack.gtfs.feed.GtfsFeed
import eu.transittrack.gtfs.feed.GtfsFeedRepository
import eu.transittrack.gtfs.model.Stop
import eu.transittrack.gtfs.model.StopRepository
import eu.transittrack.gtfs.model.StopTime
import eu.transittrack.gtfs.model.StopTimeRepository
import eu.transittrack.gtfs.revision.GtfsRevision
import eu.transittrack.gtfs.revision.GtfsRevisionRepository
import eu.transittrack.gtfs.revision.GtfsRevisionStatus
import eu.transittrack.gtfs.support.PostgresSliceTest

@PostgresSliceTest
class DraftRowCopierTest(
    @Autowired val dataSource: DataSource,
    @Autowired val feeds: GtfsFeedRepository,
    @Autowired val revisions: GtfsRevisionRepository,
    @Autowired val stops: StopRepository,
    @Autowired val stopTimes: StopTimeRepository,
) {
    private val jdbc = JdbcTemplate(dataSource)

    private fun revWith(
        feedId: Long,
        status: GtfsRevisionStatus,
    ) = revisions.save(GtfsRevision(feedId = feedId, status = status, sourceUrl = "u"))

    @Test
    fun `copies raw rows, re-keyed, isolated from source`() {
        val feed =
            feeds.save(
                GtfsFeed(
                    "f-${System.nanoTime()}",
                    "F",
                    null,
                    "u",
                    null,
                    true,
                    null,
                    FeedSource.API,
                    Instant.now(),
                    Instant.now(),
                ),
            )
        val base = revWith(feed.id!!, GtfsRevisionStatus.ACTIVE)
        val s =
            stops.saveAndFlush(
                Stop(base.id!!, "S1", null, "First", null, null, 45.0, 21.0, null, null, null, null, null, null, null, null),
            )
        stopTimes.saveAndFlush(
            StopTime(
                base.id!!, "T1", 1, "S1", 21600, 21600, null, null, null, null, null, null, null, null, null, null, null, null, null,
            ),
        )

        val draft = revWith(feed.id!!, GtfsRevisionStatus.DRAFT)
        val counts = DraftRowCopier(jdbc).copyRawTables(base.id!!, draft.id!!)

        assertThat(counts["stops"]).isEqualTo(1L)
        assertThat(counts["stop_times"]).isEqualTo(1L)

        val copiedStops = stops.findByRevisionId(draft.id!!)
        assertThat(copiedStops).hasSize(1)
        assertThat(copiedStops[0].stopName).isEqualTo("First")
        assertThat(copiedStops[0].id).isNotEqualTo(s.id)
        assertThat(copiedStops[0].revisionId).isEqualTo(draft.id)

        val copy = copiedStops[0]
        copy.stopName = "Changed"
        stops.saveAndFlush(copy)

        val baseStop = stops.findByStopId(base.id!!, "S1")
        assertThat(baseStop).isNotNull()
        assertThat(baseStop!!.stopName).isEqualTo("First")
    }

    @Test
    fun `derived tables are not in DraftRawTables`() {
        assertThat(DraftRawTables.NAMES).doesNotContain("schedule_time")
        assertThat(DraftRawTables.NAMES).doesNotContain("trip_patterns")
        assertThat(DraftRawTables.NAMES).doesNotContain("block")
    }
}
