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

import eu.transittrack.AvlAssignmentMode
import eu.transittrack.AvlFormat
import eu.transittrack.FeedsProperties
import eu.transittrack.avl.model.AvlFeed
import eu.transittrack.avl.model.AvlFeedRepository
import eu.transittrack.avl.model.AvlFeedSourceKind
import eu.transittrack.gtfs.support.PostgresSliceTest

@PostgresSliceTest
class AvlFeedConfigSynchronizerTest(
    @Autowired val repo: AvlFeedRepository,
) {
    private fun feed(
        code: String,
        url: String,
        avl: FeedsProperties.AvlDef? = FeedsProperties.AvlDef(url = "https://$code.test/vp.pb"),
    ) = FeedsProperties.FeedDef(code = code, name = code.uppercase(), url = url, avl = avl)

    private fun sync(
        prune: Boolean = false,
        vararg feeds: FeedsProperties.FeedDef,
    ) = AvlFeedConfigSynchronizer(
        repo,
        FeedsProperties(pruneConfigFeeds = prune, feeds = feeds.toList()),
    ).sync()

    @Test
    fun `inserts then updates config feeds, linking gtfsFeedCode to the feed code`() {
        sync(feeds = arrayOf(feed("a", "https://a.test/gtfs.zip")))
        val inserted = repo.findByCode("a")!!
        assertThat(inserted.url).isEqualTo("https://a.test/vp.pb")
        assertThat(inserted.gtfsFeedCode).isEqualTo("a")
        assertThat(inserted.name).isEqualTo("A vehicle positions")
        assertThat(inserted.source).isEqualTo(AvlFeedSourceKind.CONFIG)

        sync(
            feeds =
                arrayOf(
                    feed(
                        "a",
                        "https://a.test/gtfs.zip",
                        FeedsProperties.AvlDef(
                            url = "https://a.test/v2.pb",
                            name = "A realtime",
                            pollIntervalSec = 30,
                            assignmentMode = AvlAssignmentMode.TRUST_DESCRIPTOR,
                        ),
                    ),
                ),
        )
        val updated = repo.findByCode("a")!!
        assertThat(updated.url).isEqualTo("https://a.test/v2.pb")
        assertThat(updated.name).isEqualTo("A realtime")
        assertThat(updated.gtfsFeedCode).isEqualTo("a")
        assertThat(updated.pollIntervalSec).isEqualTo(30)
        assertThat(updated.assignmentMode).isEqualTo(AvlAssignmentMode.TRUST_DESCRIPTOR)
    }

    @Test
    fun `ignores feeds without an avl block`() {
        sync(feeds = arrayOf(feed("a", "https://a.test/gtfs.zip", avl = null)))
        assertThat(repo.findByCode("a")).isNull()
    }

    @Test
    fun `prune removes config feeds no longer configured`() {
        sync(feeds = arrayOf(feed("a", "https://a.test/gtfs.zip"), feed("b", "https://b.test/gtfs.zip")))
        sync(feeds = arrayOf(feed("a", "https://a.test/gtfs.zip")))
        assertThat(repo.findByCode("b")).isNotNull()
        sync(prune = true, feeds = arrayOf(feed("a", "https://a.test/gtfs.zip")))
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
                gtfsFeedCode = "a",
                url = "https://x.test/z.pb",
                format = AvlFormat.GTFS_RT,
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
            sync(feeds = arrayOf(feed("a", "https://a.test/gtfs.zip")))
        }.isInstanceOf<IllegalStateException>()
    }
}
