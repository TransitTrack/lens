package eu.transittrack.concurrency

import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledFuture
import jakarta.annotation.PreDestroy

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.DisposableBean
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler

import eu.transittrack.avl.model.AvlFeed
import eu.transittrack.avl.model.AvlFeedRepository

/**
 * Shared reconciliation core for [eu.transittrack.avl.ingest.AvlPoller],
 * [eu.transittrack.avl.match.AvlMatchProcessor], and [eu.transittrack.predict.PredictionProcessor]:
 * each owns one `scheduleWithFixedDelay` task per enabled, eligible `avl_feed` row on its own
 * dedicated [ThreadPoolTaskScheduler], reconciled on its own cadence against the database and
 * against a [FeedLockCoordinator] — one coordinator instance per processor *type* (poll/match/
 * predict), so a feed is independently owned for each concern rather than all-or-nothing.
 *
 * A subclass supplies [isEligible]/[intervalFor]/[run] plus its own `@Scheduled`/`@SchedulerLock`-
 * annotated `reconcile()` override (the lock name and cadence must be distinct per processor type,
 * so they can't live here) that simply delegates to [reconcileTasks]. [start] (fired once at
 * startup) and [destroy]/[runningFeedIds] need no per-subclass customization and live here
 * directly.
 */
abstract class FeedReconciler(
    protected val feeds: AvlFeedRepository,
    private val feedLocks: FeedLockCoordinator,
    threadNamePrefix: String,
    useVirtualThreads: Boolean,
) : DisposableBean {
    protected val log: Logger = LoggerFactory.getLogger(javaClass)

    private val scheduler =
        ThreadPoolTaskScheduler().apply {
            poolSize = 4
            setVirtualThreads(useVirtualThreads)
            setThreadNamePrefix(threadNamePrefix)
            initialize()
        }

    private data class Task(
        val future: ScheduledFuture<*>,
        val interval: Duration,
    )

    private val tasks = ConcurrentHashMap<String, Task>()

    /** Whether [feed] should currently be scheduled at all, beyond just being `enabled`. Default:
     * every enabled feed is eligible. */
    protected open fun isEligible(feed: AvlFeed): Boolean = true

    /** The recurring task's delay for [feed] — may vary per feed (e.g. [AvlFeed.pollIntervalSec])
     * or be a fixed value from this processor's own config. */
    protected abstract fun intervalFor(feed: AvlFeed): Duration

    /** The per-tick task body for the feed identified by [feedCode]. */
    protected abstract fun run(feedCode: String)

    @EventListener(ApplicationReadyEvent::class)
    open fun start() = reconcile()

    /** Each subclass overrides this with its own `@Scheduled`/`@SchedulerLock` (a distinct lock
     * name and cadence per processor type) delegating to [reconcileTasks]. */
    abstract fun reconcile()

    /**
     * Diffs the database against the running tasks: starts new/enabled/eligible feeds, cancels
     * gone/disabled/ineligible ones, and reschedules a feed whose [intervalFor] changed. Each task
     * body re-fetches its own feed row (see [run]'s implementations) so config edits propagate
     * without a restart.
     *
     * Declared `open`, like [start]/[runningFeedIds] above ([destroy] is exempt — a Kotlin `override`
     * is open by default): this class is never itself a Spring bean, so it never gets the
     * kotlin-spring plugin's `allopen` treatment — a concrete method declared here stays `final`
     * by Kotlin's own default unless marked `open` explicitly. A `final` inherited method can't be
     * CGLIB-overridden on the `@SchedulerLock`-proxied subclass, so calling it through the
     * externally-visible (proxied) bean reference would run directly on the bare, never-constructed
     * proxy shell instead of delegating to the real, fully-initialized target — reading only
     * null/default field values.
     */
    protected open fun reconcileTasks() {
        val eligible = feeds.findAllEnabled().filter { isEligible(it) }.associateBy { it.code }
        val ownedCodes = feedLocks.reconcileOwnership(eligible.keys)
        tasks.keys
            .filter { it !in ownedCodes }
            .forEach { code -> tasks.remove(code)?.future?.cancel(false) }
        for (code in ownedCodes) {
            val feed = eligible.getValue(code)
            val interval = intervalFor(feed)
            val existing = tasks[code]
            if (existing == null || existing.interval != interval) {
                existing?.future?.cancel(false)
                val future = scheduler.scheduleWithFixedDelay({ run(code) }, interval)
                tasks[code] = Task(future, interval)
            }
        }
    }

    /** test hook */
    open fun runningFeedIds(): Set<String> = tasks.keys.toSet()

    @PreDestroy
    override fun destroy() {
        // Releases exactly the codes this instance registered, not feedLocks.releaseAll(): the
        // "avl-feed" coordinator bean is shared between AvlPoller and AvlMatchProcessor, so a blind
        // releaseAll() here would also drop the other processor's held locks.
        tasks.keys.toList().forEach { feedLocks.release(it) }
        scheduler.shutdown()
    }
}
