package eu.transittrack.avl.ingest

import java.time.Instant

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

import eu.transittrack.avl.model.AvlFeedRepository

/**
 * Records `last_poll_*` in its own transaction (REQUIRES_NEW) so a poll failure recorded by
 * [AvlIngestService.pollOnce] is persisted even though the enclosing poll transaction itself rolls
 * back. Calling this through self-invocation from [AvlIngestService] would silently downgrade it to
 * the enclosing (about-to-roll-back) transaction, which is exactly the bug this bean avoids.
 */
@Component
class AvlPollRecorder(
    private val feeds: AvlFeedRepository,
) {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun recordPoll(
        feedId: Long,
        status: String,
        count: Int,
    ) {
        val f = feeds.findById(feedId).orElseThrow()
        f.lastPollAt = Instant.now()
        f.lastPollStatus = status
        f.lastPollReportCount = count
        feeds.save(f)
    }
}
