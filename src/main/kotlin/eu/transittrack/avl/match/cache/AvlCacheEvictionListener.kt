package eu.transittrack.avl.match.cache

import org.springframework.cache.CacheManager
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

import eu.transittrack.gtfs.revision.RevisionDerivedRowsChangedEvent

/**
 * Clears every AVL match read-cache ([AvlCaches.NAMES]) whenever a revision's derived rows change.
 *
 * The caches key on `revisionId`, but that key alone is not enough: `DerivationService.rederive`
 * rebuilds a revision's derived tables in place (new row ids, same `revisionId`), and on activation
 * a concurrent match cycle could repopulate from the pre-swap `activeRevisionId`. Eviction runs
 * `AFTER_COMMIT` so a concurrent reader cannot repopulate from pre-commit state;
 * `fallbackExecution = true` also fires it when there is no surrounding transaction.
 */
@Component
class AvlCacheEvictionListener(
    private val cacheManager: CacheManager,
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    fun onRevisionDerivedRowsChanged(event: RevisionDerivedRowsChangedEvent) {
        AvlCaches.NAMES.forEach { cacheManager.getCache(it)?.clear() }
    }
}
