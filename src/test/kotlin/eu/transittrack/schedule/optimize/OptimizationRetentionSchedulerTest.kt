package eu.transittrack.schedule.optimize

import java.sql.Timestamp
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate

import eu.transittrack.gtfs.support.PostgresSliceTest
import eu.transittrack.observability.TransitTrackMetrics
import eu.transittrack.support.PostgresPerMethodTest

/**
 * Drives [OptimizationRetentionScheduler.prune] directly (like [eu.transittrack.predict.PredictionRetentionSchedulerTest])
 * against a real Postgres schema: `schedule_optimization_run.feed_id`/`revision_id` carry no FK
 * constraint (see migration `0007-optimization-runs.yaml`), so rows can be seeded with arbitrary
 * ids without a real feed/revision.
 */
@PostgresSliceTest
class OptimizationRetentionSchedulerTest(
    @Autowired val jdbc: JdbcTemplate,
) : PostgresPerMethodTest() {
    private val now = Instant.parse("2026-09-13T12:00:00Z")
    private lateinit var scheduler: OptimizationRetentionScheduler

    @BeforeEach
    fun seed() {
        scheduler = OptimizationRetentionScheduler(jdbc, OptimizationProperties(), TransitTrackMetrics.forTests())

        // Old run (past the 90-day default retention) with a child recommendation that must
        // cascade-delete with it.
        jdbc.update(
            "insert into schedule_optimization_run " +
                "(id, feed_id, revision_id, observed_from, observed_to, minimum_samples, state, created_at) " +
                "values (1, 1, 1, ?, ?, 1, 'SUCCEEDED', ?)",
            Timestamp.from(now.minus(100, ChronoUnit.DAYS)),
            Timestamp.from(now.minus(93, ChronoUnit.DAYS)),
            Timestamp.from(now.minus(100, ChronoUnit.DAYS)),
        )
        jdbc.update(
            "insert into schedule_optimization_recommendation (id, run_id, kind, sample_count, delta_sec, reason) " +
                "values (1, 1, 'STOP_TIME', 5, 30, 'old')",
        )

        // Fresh run, retained, with its own recommendation, also retained.
        jdbc.update(
            "insert into schedule_optimization_run " +
                "(id, feed_id, revision_id, observed_from, observed_to, minimum_samples, state, created_at) " +
                "values (2, 1, 1, ?, ?, 1, 'SUCCEEDED', ?)",
            Timestamp.from(now.minus(2, ChronoUnit.DAYS)),
            Timestamp.from(now.minus(1, ChronoUnit.DAYS)),
            Timestamp.from(now.minus(2, ChronoUnit.DAYS)),
        )
        jdbc.update(
            "insert into schedule_optimization_recommendation (id, run_id, kind, sample_count, delta_sec, reason) " +
                "values (2, 2, 'STOP_TIME', 5, 30, 'fresh')",
        )
    }

    @Test
    fun `prunes runs older than the retention window, cascading their recommendations, and retains fresh rows`() {
        val counts = scheduler.prune(now)

        assertThat(counts).isEqualTo(OptimizationRetentionScheduler.PruneCounts(runs = 1))
        assertThat(jdbc.queryForList("select id from schedule_optimization_run", Long::class.java)).containsExactly(2L)
        assertThat(jdbc.queryForList("select id from schedule_optimization_recommendation", Long::class.java)).containsExactly(2L)
    }
}
