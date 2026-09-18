package eu.transittrack.gtfs.ingest

import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

import net.javacrumbs.shedlock.core.LockConfiguration
import net.javacrumbs.shedlock.core.LockingTaskExecutor
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.scheduling.support.CronExpression
import org.springframework.stereotype.Component

import eu.transittrack.GtfsProperties
import eu.transittrack.config.ConditionalOnRole
import eu.transittrack.config.Role
import eu.transittrack.gtfs.feed.GtfsFeedRepository

/**
 * Periodic sweep that triggers ingestion for enabled feeds whose per-feed [GtfsFeed.pollingCron]
 * fire time has elapsed since their last ingest. Guarded by `transittrack.gtfs.polling.enabled`;
 * each due feed's ingest call is wrapped in a scoped ShedLock critical section (rather than a
 * continuous [eu.transittrack.concurrency.FeedLockCoordinator] lease, since this sweep is a
 * one-shot check-and-trigger, not a persistent per-feed background loop) so two `role-ingester`
 * replicas racing on the same sweep tick never both ingest the same feed.
 */
@Component
@ConditionalOnProperty("transittrack.gtfs.polling.enabled", havingValue = "true")
@ConditionalOnRole(Role.INGESTER)
class GtfsIngestScheduler(
    private val feeds: GtfsFeedRepository,
    private val ingestion: IngestionService,
    private val props: GtfsProperties,
    private val lockingExecutor: LockingTaskExecutor,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "\${transittrack.gtfs.polling.sweep-cron}")
    fun sweep() {
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.now(zone)
        for (feed in feeds.findAllEnabled()) {
            val cron = feed.pollingCron ?: continue
            val since = LocalDateTime.ofInstant(feed.lastIngestAt ?: Instant.EPOCH, zone)
            val next =
                runCatching {
                    CronExpression.parse(cron).next(since)
                }.getOrNull() ?: continue

            if (next.isAfter(now)) {
                continue
            }

            val config =
                LockConfiguration(
                    Instant.now(), "gtfs-ingest:${feed.code}",
                    Duration.ofMinutes(30), Duration.ofSeconds(1),
                )
            lockingExecutor.executeWithLock(
                Runnable {
                    runCatching { ingestion.ingest(feed.code) }
                        .onFailure { log.warn("scheduled ingest for '{}' skipped: {}", feed.code, it.message) }
                },
                config,
            )
        }
    }
}
