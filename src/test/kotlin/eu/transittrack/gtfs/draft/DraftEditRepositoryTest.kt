package eu.transittrack.gtfs.draft

import java.time.Instant
import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.jpa.test.autoconfigure.AutoConfigureTestEntityManager
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager

import eu.transittrack.gtfs.feed.FeedSource
import eu.transittrack.gtfs.feed.GtfsFeed
import eu.transittrack.gtfs.feed.GtfsFeedRepository
import eu.transittrack.gtfs.revision.GtfsRevision
import eu.transittrack.gtfs.revision.GtfsRevisionRepository
import eu.transittrack.gtfs.revision.GtfsRevisionStatus
import eu.transittrack.gtfs.support.PostgresSliceTest

@PostgresSliceTest
@AutoConfigureTestEntityManager
class DraftEditRepositoryTest(
    @Autowired val repo: DraftEditRepository,
    @Autowired val feedRepo: GtfsFeedRepository,
    @Autowired val revisionRepo: GtfsRevisionRepository,
    @Autowired val em: TestEntityManager,
) {
    private fun feed() =
        feedRepo.save(
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

    private fun revision(feedId: Long) =
        revisionRepo.save(
            GtfsRevision(
                feedId = feedId,
                status = GtfsRevisionStatus.ACTIVE,
                sourceUrl = "http://x/z.zip",
                filesPresent = listOf("agency.txt"),
                rowCounts = mapOf("gtfs_agency" to 1L),
                createdAt = Instant.now(),
            ),
        )

    private fun edit(
        revId: Long,
        seq: Int,
        undone: Boolean = false,
    ) = repo.save(DraftEdit(revId, seq, "SHIFT_TRIP", "shift +60s", "{}", "{}", Instant.now(), undone))

    @Test
    fun `top non-undone and top undone`() {
        val f = feed()
        val rev = revision(f.id!!)
        edit(rev.id!!, 1)
        edit(rev.id!!, 2)
        edit(rev.id!!, 3, undone = true)
        assertThat(repo.findTopByRevisionIdAndUndoneFalseOrderBySeqDesc(rev.id!!)!!.seq).isEqualTo(2)
        assertThat(repo.findTopByRevisionIdAndUndoneTrueOrderBySeqAsc(rev.id!!)!!.seq).isEqualTo(3)
        assertThat(repo.deleteByRevisionIdAndUndoneTrue(rev.id!!)).isEqualTo(1)
        assertThat(repo.findByRevisionIdOrderBySeqAsc(rev.id!!)).hasSize(2)
    }
}
