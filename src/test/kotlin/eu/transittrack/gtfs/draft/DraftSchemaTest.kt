package eu.transittrack.gtfs.draft

import java.time.Instant
import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.springframework.beans.factory.annotation.Autowired

import eu.transittrack.gtfs.feed.FeedSource
import eu.transittrack.gtfs.feed.GtfsFeed
import eu.transittrack.gtfs.feed.GtfsFeedRepository
import eu.transittrack.gtfs.revision.GtfsRevision
import eu.transittrack.gtfs.revision.GtfsRevisionRepository
import eu.transittrack.gtfs.revision.GtfsRevisionStatus
import eu.transittrack.gtfs.support.PostgresSliceTest

@PostgresSliceTest
class DraftSchemaTest(
    @Autowired val feeds: GtfsFeedRepository,
    @Autowired val revisions: GtfsRevisionRepository,
) {
    @Test
    fun `draft columns round-trip`() {
        val feed = feeds
            .save(GtfsFeed("f-${System.nanoTime()}", "F", null, "u", null, true, null, FeedSource.API, Instant.now(), Instant.now()))
        val base = revisions.save(
            GtfsRevision(feedId = feed.id!!, status = GtfsRevisionStatus.ACTIVE, sourceUrl = "u"),
        )
        val saved = revisions.save(
            GtfsRevision(feedId = feed.id!!, status = GtfsRevisionStatus.DRAFT, sourceUrl = "u").apply {
                kind = DraftKind.DRAFT
                label = "Summer 2027"
                baseRevisionId = base.id!!
                derivationStale = true
                version = 3
                createdBy = "alice"
            },
        )
        val loaded = revisions.findById(saved.id!!).get()
        assertThat(loaded.kind).isEqualTo(DraftKind.DRAFT)
        assertThat(loaded.label).isEqualTo("Summer 2027")
        assertThat(loaded.derivationStale).isEqualTo(true)
        assertThat(loaded.version).isEqualTo(3L)
        assertThat(loaded.baseRevisionId).isEqualTo(base.id)
    }
}
