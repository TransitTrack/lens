package eu.transittrack.avl.ingest

import java.time.Instant
import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean

import eu.transittrack.AvlAssignmentMode
import eu.transittrack.AvlFormat
import eu.transittrack.avl.model.AvlFeed
import eu.transittrack.avl.model.AvlFeedRepository
import eu.transittrack.avl.model.AvlFeedSourceKind
import eu.transittrack.support.PostgresPerMethodTest

@SpringBootTest(
    classes = [eu.transittrack.Application::class],
    properties = ["transittrack.avl.enabled=true"],
)
class AvlPollerTest(
    @Autowired val poller: AvlPoller,
    @Autowired val feeds: AvlFeedRepository,
) : PostgresPerMethodTest() {
    @MockitoBean
    lateinit var ingest: AvlIngestService

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
}
