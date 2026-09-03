package eu.transittrack.gtfs.revision

import java.time.Instant
import kotlin.test.Test

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isTrue
import assertk.assertions.key
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.jpa.test.autoconfigure.AutoConfigureTestEntityManager
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager
import org.springframework.dao.DataIntegrityViolationException

import eu.transittrack.gtfs.feed.FeedSource
import eu.transittrack.gtfs.feed.GtfsFeed
import eu.transittrack.gtfs.feed.GtfsFeedRepository
import eu.transittrack.gtfs.support.PostgresSliceTest

@PostgresSliceTest
@AutoConfigureTestEntityManager
class RevisionRepositoryTest(
    @Autowired val feeds: GtfsFeedRepository,
    @Autowired val revisions: GtfsRevisionRepository,
    @Autowired val em: TestEntityManager,
) {
    private fun feed() =
        feeds.save(
            GtfsFeed(
                code = "f1",
                name = "F1",
                description = null,
                url = "http://x/z.zip",
                pollingCron = null,
                enabled = true,
                autoActivate = null,
                source = FeedSource.API,
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
            ),
        )

    private fun newRev(
        feedId: Long,
        status: GtfsRevisionStatus,
    ) = GtfsRevision(
        feedId = feedId,
        status = status,
        sourceUrl = "http://x/z.zip",
        filesPresent = listOf("agency.txt"),
        rowCounts = mapOf("gtfs_agency" to 1L),
        createdAt = Instant.now(),
    )

    @Test
    fun `jsonb round-trips and only one ACTIVE per feed`() {
        val f = feed()
        val r = revisions.save(newRev(f.id!!, GtfsRevisionStatus.ACTIVE))

        // Force a real INSERT flush, then detach so findById issues a SELECT
        // and the jsonb -> List/Map deserialization path is exercised.
        em.flush()
        em.clear()

        val loaded = revisions.findById(r.id!!).get()
        assertThat(loaded.filesPresent).isEqualTo(listOf("agency.txt"))
        assertThat(loaded.rowCounts).key("gtfs_agency").isEqualTo(1L)

        assertFailure {
            revisions.saveAndFlush(newRev(f.id!!, GtfsRevisionStatus.ACTIVE))
        }.isInstanceOf<DataIntegrityViolationException>()
    }

    @Test
    fun `finds non-terminal revision`() {
        val f = feed()
        revisions.save(newRev(f.id!!, GtfsRevisionStatus.PARSING))
        assertThat(
            revisions.existsByFeedAndStatusIn(
                f.id!!,
                listOf(
                    GtfsRevisionStatus.PENDING,
                    GtfsRevisionStatus.DOWNLOADING,
                    GtfsRevisionStatus.PARSING,
                    GtfsRevisionStatus.VALIDATING,
                ),
            ),
        ).isTrue()
    }
}
