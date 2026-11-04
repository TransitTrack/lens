package eu.transittrack.gtfs.draft

import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

import eu.transittrack.gtfs.revision.GtfsRevisionRepository

/**
 * On boot, clears any `gtfs_revision.deriving = true` left dangling by a rebuild whose JVM was
 * killed mid-run. The rebuild job registry is in-memory, so no rebuild can legitimately be in
 * flight at startup — any `deriving = true` is stale and would otherwise block `submitRebuild`
 * forever. Mirrors [eu.transittrack.avl.ingest.AvlPoller]'s `ApplicationReadyEvent` pattern.
 */
@Component
class DraftDerivingRecovery(
    private val revisions: GtfsRevisionRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @EventListener(ApplicationReadyEvent::class)
    fun clearDangling() {
        val cleared = revisions.clearDanglingDeriving()
        if (cleared > 0) {
            log.warn("cleared {} dangling deriving=true revision(s) left by an interrupted rebuild", cleared)
        }
    }
}
