package eu.transittrack.avl.match

import java.time.Instant
import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

import eu.transittrack.AvlAssignmentMode
import eu.transittrack.AvlFormat
import eu.transittrack.avl.model.AvlFeed
import eu.transittrack.avl.model.AvlFeedRepository
import eu.transittrack.avl.model.AvlFeedSourceKind
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
) : PostgresPerMethodTest() {
    private fun feed(enabled: Boolean = true) =
        AvlFeed(
            code = "sched-f", name = "F", gtfsFeedCode = "g", url = "https://x.test/vp.pb",
            format = AvlFormat.GTFS_RT, pollIntervalSec = 15,
            assignmentMode = AvlAssignmentMode.FULL_INFERENCE, enabled = enabled, headers = null,
            source = AvlFeedSourceKind.CONFIG, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH,
        )

    @Test
    fun `reconcile starts a task for an enabled feed`() {
        val saved = feeds.save(feed(enabled = true))
        processor.reconcile()
        assertThat(processor.runningFeedIds()).contains(saved.id!!)
    }

    @Test
    fun `reconcile cancels the task when a feed is disabled`() {
        val saved = feeds.save(feed(enabled = true))
        processor.reconcile()
        assertThat(processor.runningFeedIds()).contains(saved.id!!)

        saved.enabled = false
        feeds.save(saved)
        processor.reconcile()
        assertThat(processor.runningFeedIds()).doesNotContain(saved.id!!)
    }
}
