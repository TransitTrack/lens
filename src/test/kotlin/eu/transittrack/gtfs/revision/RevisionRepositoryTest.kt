package eu.transittrack.gtfs.revision

import eu.transittrack.gtfs.feed.FeedSource
import eu.transittrack.gtfs.feed.GtfsFeed
import eu.transittrack.gtfs.feed.GtfsFeedRepository
import eu.transittrack.gtfs.support.PostgresSliceTest
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataIntegrityViolationException

@PostgresSliceTest
class RevisionRepositoryTest(
    @Autowired val feeds: GtfsFeedRepository,
    @Autowired val revisions: GtfsRevisionRepository,
) {
    private fun feed() = feeds.save(GtfsFeed(
        code = "f1", name = "F1", description = null, url = "http://x/z.zip",
        pollingCron = null, enabled = true, autoActivate = null, source = FeedSource.API,
        createdAt = Instant.now(), updatedAt = Instant.now(),
    ))

    private fun rev(feedId: Long, status: GtfsRevisionStatus) = revisions.save(GtfsRevision(
        feedId = feedId, status = status, sourceUrl = "http://x/z.zip",
        filesPresent = listOf("agency.txt"), rowCounts = mapOf("gtfs_agency" to 1L),
        createdAt = Instant.now(),
    ))

    @Test
    fun `jsonb round-trips and only one ACTIVE per feed`() {
        val f = feed()
        val r = rev(f.id!!, GtfsRevisionStatus.ACTIVE)
        val loaded = revisions.findById(r.id!!).get()
        assertEquals(listOf("agency.txt"), loaded.filesPresent)
        assertEquals(1L, loaded.rowCounts["gtfs_agency"])

        assertFailsWith<DataIntegrityViolationException> {
            revisions.saveAndFlush(rev(f.id!!, GtfsRevisionStatus.ACTIVE))
        }
    }

    @Test
    fun `finds non-terminal revision`() {
        val f = feed()
        rev(f.id!!, GtfsRevisionStatus.PARSING)
        assertEquals(true, revisions.existsByFeedIdAndStatusIn(
            f.id!!,
            listOf(GtfsRevisionStatus.PENDING, GtfsRevisionStatus.DOWNLOADING,
                   GtfsRevisionStatus.PARSING, GtfsRevisionStatus.VALIDATING),
        ))
    }
}
