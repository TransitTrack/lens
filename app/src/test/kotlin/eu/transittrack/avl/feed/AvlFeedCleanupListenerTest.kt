package eu.transittrack.avl.feed

import java.time.Instant
import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isNotNull
import org.springframework.beans.factory.annotation.Autowired

import eu.transittrack.AvlAssignmentMode
import eu.transittrack.AvlFormat
import eu.transittrack.avl.model.AvlFeed
import eu.transittrack.avl.model.AvlFeedRepository
import eu.transittrack.avl.model.AvlFeedSourceKind
import eu.transittrack.gtfs.feed.GtfsFeedDeletedEvent
import eu.transittrack.gtfs.support.PostgresSliceTest
import eu.transittrack.support.PostgresPerMethodTest

@PostgresSliceTest
class AvlFeedCleanupListenerTest(
    @Autowired val repo: AvlFeedRepository,
) : PostgresPerMethodTest() {
    private fun avlFeed(
        code: String,
        gtfsFeedCode: String = code,
    ): AvlFeed {
        val now = Instant.now()
        return AvlFeed(
            code = code,
            name = "$code vehicle positions",
            gtfsFeedCode = gtfsFeedCode,
            url = "https://$code.test/vp.pb",
            format = AvlFormat.GTFS_RT,
            pollIntervalSec = 30,
            assignmentMode = AvlAssignmentMode.FULL_INFERENCE,
            enabled = true,
            headers = null,
            source = AvlFeedSourceKind.API,
            createdAt = now,
            updatedAt = now,
        )
    }

    @Test
    fun `deletes the avl feed linked to the deleted gtfs feed`() {
        repo.save(avlFeed("w"))

        AvlFeedCleanupListener(repo).onGtfsFeedDeleted(GtfsFeedDeletedEvent("w"))

        assertThat(repo.findByGtfsFeedCode("w")).isEmpty()
    }

    @Test
    fun `leaves other feeds' avl rows alone`() {
        repo.save(avlFeed("w"))
        repo.save(avlFeed("other"))

        AvlFeedCleanupListener(repo).onGtfsFeedDeleted(GtfsFeedDeletedEvent("w"))

        assertThat(repo.findByCode("other")).isNotNull()
    }
}
