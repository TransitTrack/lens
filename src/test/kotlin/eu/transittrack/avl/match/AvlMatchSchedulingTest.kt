package eu.transittrack.avl.match

import java.time.Instant
import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest

import eu.transittrack.AvlAssignmentMode
import eu.transittrack.AvlFormat
import eu.transittrack.avl.model.AvlFeed
import eu.transittrack.avl.model.AvlFeedRepository
import eu.transittrack.avl.model.AvlFeedSourceKind
import eu.transittrack.concurrency.FeedLockCoordinator
import eu.transittrack.gtfs.feed.FeedSource
import eu.transittrack.gtfs.feed.GtfsFeed
import eu.transittrack.gtfs.feed.GtfsFeedRepository
import eu.transittrack.gtfs.revision.GtfsRevision
import eu.transittrack.gtfs.revision.GtfsRevisionRepository
import eu.transittrack.gtfs.revision.GtfsRevisionStatus
import eu.transittrack.support.PostgresPerMethodTest

@SpringBootTest(
    classes = [eu.transittrack.Application::class],
    properties = [
        "transittrack.avl.enabled=true",
        "transittrack.avl.match.match-interval-ms=3600000",
    ],
)
class AvlMatchSchedulingTest(
    @Autowired val processor: AvlMatchProcessor,
    @Autowired val feeds: AvlFeedRepository,
    @Autowired val gtfsFeeds: GtfsFeedRepository,
    @Autowired val revisions: GtfsRevisionRepository,
    @Autowired @Qualifier("avlFeedLockCoordinator") val sharedCoordinator: FeedLockCoordinator,
    @Autowired val lockProvider: JdbcTemplateLockProvider,
) : PostgresPerMethodTest() {
    // See AvlPollerTest for why both resets are needed: this class's cached Spring context (and
    // its singleton coordinator + lock provider bean) survives across @Test methods, but each
    // method truncates every table, including `shedlock`.
    // clearCache() must run *after* the truncate, not before: a background @Scheduled reconcile
    // tick can race the truncate and re-populate the cache for a name in between an earlier
    // clearCache() call and the truncate actually deleting its row (see AvlPollerTest).
    @BeforeEach
    override fun truncateBeforeEachTest() {
        sharedCoordinator.releaseAll()
        super.truncateBeforeEachTest()
        lockProvider.clearCache()
    }

    private fun feed(enabled: Boolean = true) =
        AvlFeed(
            code = "sched-f", name = "F", gtfsFeedCode = "g", url = "https://x.test/vp.pb",
            format = AvlFormat.GTFS_RT, pollIntervalSec = 15,
            assignmentMode = AvlAssignmentMode.FULL_INFERENCE, enabled = enabled, headers = null,
            source = AvlFeedSourceKind.CONFIG, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH,
        )

    /** Gives GTFS feed "g" an active revision, so [AvlMatchProcessor.reconcile] considers an AVL
     * feed pointed at it ready to match — see [eu.transittrack.avl.ingest.AvlPollerTest]. */
    private fun activateGtfsFeed() {
        val gtfsFeed =
            gtfsFeeds.save(
                GtfsFeed(
                    code = "g", name = "G", description = null, url = "https://x.test/g.zip",
                    pollingCron = null, enabled = true, autoActivate = true, source = FeedSource.CONFIG,
                    createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH,
                ),
            )
        revisions.save(GtfsRevision(feedId = gtfsFeed.id!!, status = GtfsRevisionStatus.ACTIVE, sourceUrl = "u"))
    }

    @Test
    fun `reconcile starts a task for an enabled feed`() {
        activateGtfsFeed()
        val saved = feeds.save(feed(enabled = true))
        processor.reconcile()
        assertThat(processor.runningFeedIds()).contains(saved.code)
    }

    @Test
    fun `reconcile cancels the task when a feed is disabled`() {
        activateGtfsFeed()
        val saved = feeds.save(feed(enabled = true))
        processor.reconcile()
        assertThat(processor.runningFeedIds()).contains(saved.code)

        saved.enabled = false
        feeds.save(saved)
        processor.reconcile()
        assertThat(processor.runningFeedIds()).doesNotContain(saved.code)
    }
}
