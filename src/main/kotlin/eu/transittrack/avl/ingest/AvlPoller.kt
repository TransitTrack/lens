package eu.transittrack.avl.ingest

import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledFuture
import jakarta.annotation.PreDestroy

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.stereotype.Component

import eu.transittrack.avl.model.AvlFeedRepository

/**
 * Owns one `scheduleWithFixedDelay` task per enabled `avl_feed` row. A 60s reconciler diffs the
 * database against the running tasks: starts new/enabled feeds, cancels gone/disabled ones, and
 * reschedules a feed whose `pollIntervalSec` changed. Each task body re-fetches its feed row so
 * config edits propagate without a restart.
 */
@Component
@ConditionalOnProperty("transittrack.avl.enabled", havingValue = "true")
class AvlPoller(
    private val feeds: AvlFeedRepository,
    private val ingest: AvlIngestService,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val scheduler =
        ThreadPoolTaskScheduler().apply {
            poolSize = 4
            setThreadNamePrefix("avl-poll-")
            initialize()
        }

    private data class Task(
        val future: ScheduledFuture<*>,
        val intervalSec: Int,
    )

    private val tasks = ConcurrentHashMap<Long, Task>()

    @EventListener(ApplicationReadyEvent::class)
    fun start() = reconcile()

    @Scheduled(fixedDelay = 60_000)
    @Synchronized
    fun reconcile() {
        val enabled = feeds.findAllEnabled().associateBy { it.id!! }
        tasks.keys.filter { it !in enabled }.forEach { id ->
            tasks.remove(id)?.future?.cancel(false)
        }
        for ((id, feed) in enabled) {
            val existing = tasks[id]
            if (existing == null || existing.intervalSec != feed.pollIntervalSec) {
                existing?.future?.cancel(false)
                val future =
                    scheduler.scheduleWithFixedDelay(
                        { pollFeed(id) },
                        Duration.ofSeconds(feed.pollIntervalSec.toLong()),
                    )
                tasks[id] = Task(future, feed.pollIntervalSec)
            }
        }
    }

    private fun pollFeed(feedId: Long) {
        val feed = feeds.findById(feedId).orElse(null) ?: return
        if (!feed.enabled) return
        runCatching { ingest.pollOnce(feed) }
            .onFailure { log.warn("avl poll for feed '{}' failed: {}", feed.code, it.message) }
    }

    /** test hook */
    fun runningFeedIds(): Set<Long> = tasks.keys.toSet()

    @PreDestroy
    fun shutdown() = scheduler.shutdown()
}
