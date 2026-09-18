package eu.transittrack.avl.ingest

import java.time.Duration
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
import org.springframework.test.context.bean.override.mockito.MockitoBean

import eu.transittrack.AvlAssignmentMode
import eu.transittrack.AvlFormat
import eu.transittrack.avl.model.AvlFeed
import eu.transittrack.avl.model.AvlFeedRepository
import eu.transittrack.avl.model.AvlFeedSourceKind
import eu.transittrack.concurrency.FeedLockCoordinator
import eu.transittrack.support.PostgresPerMethodTest

@SpringBootTest(
    classes = [eu.transittrack.Application::class],
    properties = ["transittrack.avl.enabled=true"],
)
class AvlPollerTest(
    @Autowired val poller: AvlPoller,
    @Autowired val feeds: AvlFeedRepository,
    @Autowired @Qualifier("avlFeedLockCoordinator") val sharedCoordinator: FeedLockCoordinator,
    @Autowired val lockProvider: JdbcTemplateLockProvider,
) : PostgresPerMethodTest() {
    @MockitoBean
    lateinit var ingest: AvlIngestService

    // The Spring context (and this singleton coordinator + lock provider bean) is cached across
    // this class's @Test methods, but each method truncates every table, including `shedlock`.
    // FeedLockCoordinator's own in-memory `held` map would then reference rows that no longer
    // exist; separately, JdbcTemplateLockProvider (via ShedLock's StorageBasedLockProvider) keeps
    // its own in-memory registry of lock names it believes already have a row, so it stops
    // attempting INSERT for that name — an UPDATE against a truncated-away row then matches zero
    // rows and the "lock" silently fails. Reset both before the truncate that starts each test.
    @BeforeEach
    override fun truncateBeforeEachTest() {
        sharedCoordinator.releaseAll()
        lockProvider.clearCache()
        super.truncateBeforeEachTest()
    }

    private fun feed(enabled: Boolean = true) =
        AvlFeed(
            code = "poll-f", name = "F", gtfsFeedCode = "g", url = "https://x.test/vp.pb",
            format = AvlFormat.GTFS_RT, pollIntervalSec = 15,
            assignmentMode = AvlAssignmentMode.FULL_INFERENCE, enabled = enabled, headers = null,
            source = AvlFeedSourceKind.CONFIG, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH,
        )

    @Test
    fun `reconcile starts a task for an enabled feed`() {
        val saved = feeds.save(feed(enabled = true))
        poller.reconcile()
        assertThat(poller.runningFeedIds()).contains(saved.id!!)
    }

    @Test
    fun `reconcile cancels the task when a feed is disabled`() {
        val saved = feeds.save(feed(enabled = true))
        poller.reconcile()
        assertThat(poller.runningFeedIds()).contains(saved.id!!)

        saved.enabled = false
        feeds.save(saved)
        poller.reconcile()
        assertThat(poller.runningFeedIds()).doesNotContain(saved.id!!)
    }

    @Test
    fun `reconcile does not start a task for a feed already owned by another coordinator`() {
        val saved = feeds.save(feed(enabled = true))
        val otherPod = FeedLockCoordinator(lockProvider, "avl-feed", Duration.ofMinutes(3), Duration.ofSeconds(50))
        otherPod.reconcileOwnership(setOf(saved.id!!))

        poller.reconcile()
        assertThat(poller.runningFeedIds()).doesNotContain(saved.id!!)
    }
}
