package eu.transittrack.concurrency

import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

import net.javacrumbs.shedlock.core.LockConfiguration
import net.javacrumbs.shedlock.core.LockProvider
import net.javacrumbs.shedlock.core.SimpleLock

/**
 * Grants continuous, distributed ownership of feed codes to whichever caller's [reconcileOwnership]
 * wins the race for a given feed's ShedLock lock. Meant for components that run a persistent
 * per-feed background loop (poll/match/predict), where two owners running the same feed at once
 * would corrupt shared, non-atomically-claimed state (see the design doc's Problem section).
 *
 * Keyed by `avl_feed.code` rather than its numeric id: the code is the stable, human-meaningful feed
 * identity (what shows up in config/logs/ShedLock rows), whereas the id is an incidental surrogate
 * key that only matters inside this database.
 *
 * Call [reconcileOwnership] once per reconcile tick with the full candidate feed code set; it
 * extends locks already held, attempts to acquire locks for newly-candidate feeds, and releases
 * locks for feeds no longer in the candidate set. A held lock that can't be extended (e.g. it
 * expired and another instance claimed it first) is dropped from ownership.
 */
class FeedLockCoordinator(
    private val lockProvider: LockProvider,
    private val lockGroup: String,
    private val lockAtMostFor: Duration,
    private val lockAtLeastFor: Duration,
) {
    private val held = ConcurrentHashMap<String, SimpleLock>()

    @Synchronized
    fun reconcileOwnership(candidateFeedCodes: Set<String>): Set<String> {
        held.keys.filter { it !in candidateFeedCodes }.forEach { release(it) }

        for (feedCode in candidateFeedCodes) {
            val current = held[feedCode]
            if (current != null) {
                val extended = current.extend(lockAtMostFor, lockAtLeastFor)
                if (extended.isPresent) {
                    held[feedCode] = extended.get()
                } else {
                    held.remove(feedCode)
                }
            } else {
                val config = LockConfiguration(Instant.now(), lockName(feedCode), lockAtMostFor, lockAtLeastFor)
                lockProvider.lock(config).ifPresent { held[feedCode] = it }
            }
        }
        return held.keys.toSet()
    }

    @Synchronized
    fun release(feedCode: String) {
        held.remove(feedCode)?.unlock()
    }

    @Synchronized
    fun releaseAll() {
        held.keys.toList().forEach { release(it) }
    }

    private fun lockName(feedCode: String) = "$lockGroup:$feedCode"
}
