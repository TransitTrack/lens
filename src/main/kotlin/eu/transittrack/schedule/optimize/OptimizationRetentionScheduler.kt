package eu.transittrack.schedule.optimize

import java.sql.Timestamp
import java.time.Instant
import java.time.temporal.ChronoUnit

import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Age-based prune of `schedule_optimization_run` rows older than
 * [OptimizationProperties.Retention.resultsRetentionDays] (design section 9, initial default 90
 * days). Child `schedule_optimization_recommendation` rows are never deleted directly here — they
 * cascade-delete via their `fk_optimization_recommendation_run` foreign key
 * (`deleteCascade: true`, see migration `0007-optimization-runs.yaml`), so a single `run` row
 * delete removes every recommendation it produced.
 */
@Component
class OptimizationRetentionScheduler(
    private val jdbc: JdbcTemplate,
    props: OptimizationProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val resultsRetentionDays = props.retention.resultsRetentionDays.toLong()

    data class PruneCounts(
        val runs: Int,
    )

    @Scheduled(cron = "\${transittrack.schedule.optimize.retention.sweep-cron:0 30 3 * * *}")
    fun prune() {
        val counts = prune(Instant.now())
        log.info("optimization retention: pruned {} runs (recommendations cascade with them)", counts.runs)
    }

    fun prune(now: Instant): PruneCounts {
        val runs =
            jdbc.update(
                "delete from schedule_optimization_run where created_at < ?",
                Timestamp.from(now.minus(resultsRetentionDays, ChronoUnit.DAYS)),
            )
        return PruneCounts(runs)
    }
}
