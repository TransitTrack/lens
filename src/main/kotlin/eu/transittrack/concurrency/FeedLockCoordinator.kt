package eu.transittrack.concurrency

import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

import net.javacrumbs.shedlock.core.LockConfiguration
import net.javacrumbs.shedlock.core.LockProvider
import net.javacrumbs.shedlock.core.SimpleLock

/**
 * Grants continuous, distributed ownership of feed ids to whichever caller's [reconcileOwnership]
 * wins the race for a given feed's ShedLock lock. Meant for components that run a persistent
 * per-feed background loop (poll/match/predict), where two owners running the same feed at once
 * would corrupt shared, non-atomically-claimed state (see the design doc's Problem section).
 *
 * Call [reconcileOwnership] once per reconcile tick with the full candidate feed id set; it
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
    private val held = ConcurrentHashMap<Long, SimpleLock>()

    @Synchronized
    fun reconcileOwnership(candidateFeedIds: Set<Long>): Set<Long> {
        held.keys.filter { it !in candidateFeedIds }.forEach { release(it) }

        for (feedId in candidateFeedIds) {
            val current = held[feedId]
            if (current != null) {
                val extended = current.extend(lockAtMostFor, lockAtLeastFor)
                if (extended.isPresent) {
                    held[feedId] = extended.get()
                } else {
                    held.remove(feedId)
                }
            } else {
                val config = LockConfiguration(Instant.now(), lockName(feedId), lockAtMostFor, lockAtLeastFor)
                lockProvider.lock(config).ifPresent { held[feedId] = it }
            }
        }
        return held.keys.toSet()
    }

    @Synchronized
    fun release(feedId: Long) {
        held.remove(feedId)?.unlock()
    }

    @Synchronized
    fun releaseAll() {
        held.keys.toList().forEach { release(it) }
    }

    private fun lockName(feedId: Long) = "$lockGroup:$feedId"
}
