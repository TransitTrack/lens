package eu.transittrack.avl.feed

import java.time.Instant
import kotlin.test.Test

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import org.springframework.beans.factory.annotation.Autowired

import eu.transittrack.avl.AvlAssignmentMode
import eu.transittrack.avl.AvlProperties
import eu.transittrack.avl.model.AvlFeed
import eu.transittrack.avl.model.AvlFeedRepository
import eu.transittrack.avl.model.AvlFeedSourceKind
import eu.transittrack.gtfs.support.PostgresSliceTest

@PostgresSliceTest
class AvlFeedConfigSynchronizerTest(
    @Autowired val repo: AvlFeedRepository,
) {
    private fun def(
        code: String,
        url: String,
    ) = AvlProperties.AvlFeedDef(code = code, name = code.uppercase(), gtfsFeedCode = "g", url = url)

    private fun sync(
        prune: Boolean = false,
        vararg feeds: AvlProperties.AvlFeedDef,
    ) = AvlFeedConfigSynchronizer(
        repo,
        AvlProperties(pruneConfigFeeds = prune, feeds = feeds.toList()),
    ).sync()

    @Test
    fun `inserts then updates config feeds`() {
        sync(feeds = arrayOf(def("a", "https://a.test/1.pb")))
        assertThat(repo.findByCode("a")!!.url).isEqualTo("https://a.test/1.pb")
        assertThat(repo.findByCode("a")!!.source).isEqualTo(AvlFeedSourceKind.CONFIG)
        sync(
            feeds =
                arrayOf(
                    AvlProperties.AvlFeedDef(
                        code = "a",
                        name = "A2",
                        gtfsFeedCode = "g2",
                        url = "https://a.test/2.pb",
                        pollIntervalSec = 30,
                        assignmentMode = AvlAssignmentMode.TRUST_DESCRIPTOR,
                    ),
                ),
        )
        val updated = repo.findByCode("a")!!
        assertThat(updated.url).isEqualTo("https://a.test/2.pb")
        assertThat(updated.name).isEqualTo("A2")
        assertThat(updated.gtfsFeedCode).isEqualTo("g2")
        assertThat(updated.pollIntervalSec).isEqualTo(30)
        assertThat(updated.assignmentMode).isEqualTo(AvlAssignmentMode.TRUST_DESCRIPTOR)
    }

    @Test
    fun `prune removes config feeds no longer configured`() {
        sync(feeds = arrayOf(def("a", "https://a.test/1.pb"), def("b", "https://b.test/1.pb")))
        sync(feeds = arrayOf(def("a", "https://a.test/1.pb")))
        assertThat(repo.findByCode("b")).isNotNull()
        sync(prune = true, feeds = arrayOf(def("a", "https://a.test/1.pb")))
        assertThat(repo.findByCode("a")).isNotNull()
        assertThat(repo.findByCode("b")).isNull()
    }

    @Test
    fun `fails when config code collides with an API feed`() {
        val now = Instant.now()
        repo.save(
            AvlFeed(
                code = "a",
                name = "A",
                gtfsFeedCode = "g",
                url = "https://x.test/z.pb",
                format = eu.transittrack.avl.AvlFormat.GTFS_RT,
                pollIntervalSec = 15,
                assignmentMode = AvlAssignmentMode.FULL_INFERENCE,
                enabled = true,
                headers = null,
                source = AvlFeedSourceKind.API,
                createdAt = now,
                updatedAt = now,
            ),
        )
        assertFailure {
            sync(feeds = arrayOf(def("a", "https://a.test/1.pb")))
        }.isInstanceOf<IllegalStateException>()
    }
}
