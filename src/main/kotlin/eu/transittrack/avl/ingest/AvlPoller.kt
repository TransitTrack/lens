package eu.transittrack.avl.ingest

import java.time.Duration

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

import eu.transittrack.avl.match.AvlMatchContextFactory
import eu.transittrack.avl.model.AvlFeed
import eu.transittrack.avl.model.AvlFeedRepository
import eu.transittrack.concurrency.FeedLockCoordinator
import eu.transittrack.concurrency.FeedReconciler
import eu.transittrack.config.ConditionalOnRole
import eu.transittrack.config.Role

/**
 * Owns one `scheduleWithFixedDelay` task per enabled `avl_feed` row whose GTFS feed
 * ([AvlMatchContextFactory.hasActiveRevision]) currently has an active revision — polling a feed
 * with nothing to match against yet would just pile up `avl_report` rows no
 * [eu.transittrack.avl.match.AvlMatchProcessor] pass can use.
 *
 * The task lifecycle itself (reconciling against the database, interval changes, [FeedLockCoordinator]
 * ownership) lives in [FeedReconciler]; this class only supplies the feed-eligibility check, the
 * per-feed poll interval, and the actual poll.
 */
@Component
@ConditionalOnProperty("transittrack.avl.enabled", havingValue = "true")
@ConditionalOnRole(Role.FEED_PROCESSOR)
class AvlPoller(
    feeds: AvlFeedRepository,
    private val ingest: AvlIngestService,
    private val contextFactory: AvlMatchContextFactory,
    @Qualifier("avlFeedLockCoordinator") feedLocks: FeedLockCoordinator,
) : FeedReconciler(feeds, feedLocks, "avl-poll-", useVirtualThreads = false) {
    override fun isEligible(feed: AvlFeed): Boolean = contextFactory.hasActiveRevision(feed)

    override fun intervalFor(feed: AvlFeed): Duration = Duration.ofSeconds(feed.pollIntervalSec.toLong())

    override fun run(feedCode: String) {
        val feed = feeds.findByCode(feedCode) ?: return
        if (!feed.enabled) return
        runCatching { ingest.pollOnce(feed) }
            .onFailure { log.warn("avl poll for feed '{}' failed", feed.code, it) }
    }

    @Scheduled(fixedDelay = 60_000)
    // lockAtLeastFor is intentionally ~zero: this only needs to keep two pods from reconciling at
    // the exact same instant (actual feed ownership is separately serialized by feedLocks/ShedLock
    // per feed code below). A nonzero minimum instead blocks *any* re-entry into this method - even a
    // direct same-process call, since ShedLock's AOP advice wraps every call through the proxy, not
    // just scheduler-triggered ones - which would make back-to-back reconcile() calls no-op.
    @SchedulerLock(name = "avl-poller-reconcile-lock", lockAtMostFor = "PT30M", lockAtLeastFor = "PT0S")
    override fun reconcile() = reconcileTasks()
}
