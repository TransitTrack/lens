package eu.transittrack.predict

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

import eu.transittrack.config.ConditionalOnRole
import eu.transittrack.config.Role
import eu.transittrack.util.observability.TransitTrackMetrics

/**
 * Age-based prune of prediction tables, plus a sweep of learner-table rows left orphaned once no
 * revision still stored for their GTFS feed has a pattern with their `pattern_key` (the route,
 * shape or stop list changed). A revision swap that keeps a pattern keeps its learning.
 */
@Component
@ConditionalOnProperty("transittrack.predict.enabled", havingValue = "true")
@ConditionalOnRole(Role.FEED_PROCESSOR)
class PredictionRetentionScheduler(
    private val jdbc: JdbcTemplate,
    props: PredictProperties,
    private val metrics: TransitTrackMetrics,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val cfg = props.retention

    data class PruneCounts(
        val predictions: Int,
        val checkpoints: Int,
        val accuracy: Int,
        val crossings: Int,
        val orphanObservations: Int,
        val orphanKalman: Int,
    )

    @Scheduled(cron = "\${transittrack.predict.retention.sweep-cron}")
    @SchedulerLock(name = "predict-retention-prune", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    fun prune() {
        val start = Instant.now()
        val result = runCatching { prune(start) }
        result.onSuccess { c ->
            log.info(
                "predict retention: pruned {} predictions, {} checkpoints, {} accuracy, {} crossings, " +
                    "{} orphan observations, {} orphan kalman",
                c.predictions, c.checkpoints, c.accuracy, c.crossings, c.orphanObservations, c.orphanKalman,
            )
        }
        metrics.scheduledJobFinished(
            job = "predict_retention_prune",
            outcome = if (result.isSuccess) TransitTrackMetrics.Outcome.SUCCESS else TransitTrackMetrics.Outcome.FAILED,
            elapsed = Duration.between(start, Instant.now()),
            items = result.getOrNull()?.let {
                (it.predictions + it.checkpoints + it.accuracy + it.crossings + it.orphanObservations + it.orphanKalman)
                    .toLong()
            },
        )
        result.getOrThrow()
    }

    fun prune(now: Instant): PruneCounts {
        val predictions = jdbc.update(
            "delete from vehicle_prediction where computed_at < ?",
            Timestamp.from(now.minus(cfg.predictionHours, ChronoUnit.HOURS)),
        )
        // Never scored: the vehicle didn't reach the stop on that trip, or wasn't seen passing it.
        val checkpoints = jdbc.update(
            "delete from prediction_checkpoint where computed_at < ?",
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
        val orphanObservations = jdbc.update(orphanSweepSql("travel_time_observation"))
        val orphanKalman = jdbc.update(orphanSweepSql("kalman_travel_time_state"))
        return PruneCounts(predictions, checkpoints, accuracy, crossings, orphanObservations, orphanKalman)
    }
}

private fun orphanSweepSql(table: String) =
    "delete from $table t where not exists (" +
        "select 1 from trip_patterns p join gtfs_revision r on r.id = p.revision_id join gtfs_feed f on f.id = r.feed_id " +
        "where f.code = t.gtfs_feed_code and p.pattern_key = t.pattern_key)"
