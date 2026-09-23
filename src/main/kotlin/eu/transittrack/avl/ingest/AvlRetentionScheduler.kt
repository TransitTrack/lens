package eu.transittrack.avl.ingest

import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

import eu.transittrack.avl.AvlProperties
import eu.transittrack.config.ConditionalOnRole
import eu.transittrack.config.Role
import eu.transittrack.observability.TransitTrackMetrics

/**
 * Age-based prune of AVL ingestion tables. Deleting `avl_report` rows cascades their `vehicle_match`
 * rows via `fk_vehicle_match_report ON DELETE CASCADE`; the separate `vehicle_match` delete removes
 * orphan-by-age matches whose report is still within the report retention window.
 */
@Component
@ConditionalOnProperty("transittrack.avl.enabled", havingValue = "true")
@ConditionalOnRole(Role.FEED_PROCESSOR)
class AvlRetentionScheduler(
    private val jdbc: JdbcTemplate,
    props: AvlProperties,
    private val metrics: TransitTrackMetrics,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val cfg = props.retention

    data class PruneCounts(
        val reports: Int,
        val matches: Int,
        val states: Int,
    )

    @Scheduled(cron = "\${transittrack.avl.retention.sweep-cron}")
    @SchedulerLock(name = "avl-retention-prune", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    fun prune() {
        val start = Instant.now()
        val result = runCatching { prune(start) }
        result.onSuccess { c ->
            log.info("avl retention: pruned {} reports, {} matches, {} states", c.reports, c.matches, c.states)
        }
        metrics.scheduledJobFinished(
            job = "avl_retention_prune",
            outcome = if (result.isSuccess) TransitTrackMetrics.Outcome.SUCCESS else TransitTrackMetrics.Outcome.FAILED,
            elapsed = Duration.between(start, Instant.now()),
            items = result.getOrNull()?.let { (it.reports + it.matches + it.states).toLong() },
        )
        result.getOrThrow()
    }

    fun prune(now: Instant): PruneCounts {
        val reports = jdbc.update(
            "delete from avl_report where created_at < ?",
            Timestamp.from(now.minus(cfg.reportDuration)),
        )
        val matches = jdbc.update(
            "delete from vehicle_match where created_at < ?",
            Timestamp.from(now.minus(cfg.matchDuration)),
        )
        val states = jdbc.update(
            "delete from vehicle_state where updated_at < ?",
            Timestamp.from(now.minus(cfg.staleVehicleDuration)),
        )
        return PruneCounts(reports, matches, states)
    }
}
