package eu.transittrack.avl.ingest

import java.sql.Timestamp
import java.time.Instant
import java.time.temporal.ChronoUnit

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

import eu.transittrack.avl.AvlProperties

/**
 * Age-based prune of AVL ingestion tables. Deleting `avl_report` rows cascades their `vehicle_match`
 * rows via `fk_vehicle_match_report ON DELETE CASCADE`; the separate `vehicle_match` delete removes
 * orphan-by-age matches whose report is still within the report retention window.
 */
@Component
@ConditionalOnProperty("transittrack.avl.enabled", havingValue = "true")
class AvlRetentionScheduler(
    private val jdbc: JdbcTemplate,
    props: AvlProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val cfg = props.retention

    data class PruneCounts(
        val reports: Int,
        val matches: Int,
        val states: Int,
    )

    @Scheduled(cron = "\${transittrack.avl.retention.sweep-cron}")
    fun prune() {
        val c = prune(Instant.now())
        log.info("avl retention: pruned {} reports, {} matches, {} states", c.reports, c.matches, c.states)
    }

    fun prune(now: Instant): PruneCounts {
        val reports = jdbc.update(
            "delete from avl_report where created_at < ?",
            Timestamp.from(now.minus(cfg.reportHours, ChronoUnit.HOURS)),
        )
        val matches = jdbc.update(
            "delete from vehicle_match where created_at < ?",
            Timestamp.from(now.minus(cfg.matchHours, ChronoUnit.HOURS)),
        )
        val states = jdbc.update(
            "delete from vehicle_state where updated_at < ?",
            Timestamp.from(now.minus(cfg.staleVehicleHours, ChronoUnit.HOURS)),
        )
        return PruneCounts(reports, matches, states)
    }
}
