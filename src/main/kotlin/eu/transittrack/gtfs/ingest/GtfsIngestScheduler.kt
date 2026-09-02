package eu.transittrack.gtfs.ingest

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.scheduling.support.CronExpression
import org.springframework.stereotype.Component

import eu.transittrack.gtfs.GtfsProperties
import eu.transittrack.gtfs.feed.GtfsFeedRepository

/**
 * Periodic sweep that triggers ingestion for enabled feeds whose per-feed [GtfsFeed.pollingCron]
 * fire time has elapsed since their last ingest. Guarded by `transittrack.gtfs.polling.enabled`;
 * feeds already ingesting are skipped by [IngestionService.ingest]'s in-progress guard (the
 * resulting exception is logged).
 */
@Component
@ConditionalOnProperty("transittrack.gtfs.polling.enabled", havingValue = "true")
class GtfsIngestScheduler(
    private val feeds: GtfsFeedRepository,
    private val ingestion: IngestionService,
    private val props: GtfsProperties,
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

            runCatching {
                ingestion.ingest(feed.code)
            }.onFailure {
                log.warn("scheduled ingest for '{}' skipped: {}", feed.code, it.message)
            }
        }
    }
}
