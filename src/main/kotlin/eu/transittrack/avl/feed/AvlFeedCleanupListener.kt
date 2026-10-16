package eu.transittrack.avl.feed

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

import eu.transittrack.avl.model.AvlFeedRepository
import eu.transittrack.gtfs.feed.GtfsFeedDeletedEvent

/**
 * Deletes the `avl_feed` row(s) linked to a deleted `gtfs_feed`. There is no FK/JPA relationship
 * between the two (see `AvlFeed.gtfsFeedCode`), so without this the row survives the GTFS feed's
 * deletion and `AvlPoller` keeps polling it indefinitely. Runs `AFTER_COMMIT` so it only acts once
 * the GTFS feed deletion is durable; `fallbackExecution = true` also fires it when there is no
 * surrounding transaction. `REQUIRES_NEW` gives the lookup-and-delete its own transaction/
 * EntityManager instead of trying to participate in the just-completed one, whose synchronization
 * resources are still being torn down at the point `AFTER_COMMIT` callbacks run.
 */
@Component
class AvlFeedCleanupListener(
    private val repo: AvlFeedRepository,
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onGtfsFeedDeleted(event: GtfsFeedDeletedEvent) {
        repo.findByGtfsFeedCode(event.code).forEach(repo::delete)
    }
}
