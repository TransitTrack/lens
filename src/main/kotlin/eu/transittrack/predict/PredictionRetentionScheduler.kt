package eu.transittrack.predict

import java.sql.Timestamp
import java.time.Instant
import java.time.temporal.ChronoUnit

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

import eu.transittrack.config.ConditionalOnRole
import eu.transittrack.config.Role

/**
 * Age-based prune of prediction tables, plus a sweep of learner-table rows left orphaned when their
 * `trip_pattern_id` no longer exists in `trip_patterns` (e.g. after a GTFS revision swap).
 */
@Component
@ConditionalOnProperty("transittrack.predict.enabled", havingValue = "true")
@ConditionalOnRole(Role.FEED_PROCESSOR)
class PredictionRetentionScheduler(
    private val jdbc: JdbcTemplate,
    props: PredictProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val cfg = props.retention

    data class PruneCounts(
        val predictions: Int,
        val accuracy: Int,
        val crossings: Int,
        val orphanObservations: Int,
        val orphanKalman: Int,
    )

    @Scheduled(cron = "\${transittrack.predict.retention.sweep-cron}")
    @SchedulerLock(name = "predict-retention-prune", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    fun prune() {
        val c = prune(Instant.now())
        log.info(
            "predict retention: pruned {} predictions, {} accuracy, {} orphan observations, {} orphan kalman",
            c.predictions, c.accuracy, c.orphanObservations, c.orphanKalman,
        )
    }

    fun prune(now: Instant): PruneCounts {
        val predictions = jdbc.update(
            "delete from vehicle_prediction where computed_at < ?",
            Timestamp.from(now.minus(cfg.predictionHours, ChronoUnit.HOURS)),
        )
        val accuracy = jdbc.update(
            "delete from prediction_accuracy where created_at < ?",
            Timestamp.from(now.minus(cfg.accuracyDays, ChronoUnit.DAYS)),
        )
        val crossings = jdbc.update(
            "delete from avl_stop_crossing where observed_at < ?",
            Timestamp.from(now.minus(cfg.rawCrossingDays, ChronoUnit.DAYS)),
        )
        val orphanObservations = jdbc.update(
            "delete from travel_time_observation where trip_pattern_id not in (select id from trip_patterns)",
        )
        val orphanKalman = jdbc.update(
            "delete from kalman_travel_time_state where trip_pattern_id not in (select id from trip_patterns)",
        )
        return PruneCounts(predictions, accuracy, crossings, orphanObservations, orphanKalman)
    }
}
