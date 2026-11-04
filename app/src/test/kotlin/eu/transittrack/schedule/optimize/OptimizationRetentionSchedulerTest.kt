package eu.transittrack.schedule.optimize

import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired

import eu.transittrack.gtfs.support.PostgresSliceTest
import eu.transittrack.schedule.optimize.model.OptimizationRecommendationKind
import eu.transittrack.schedule.optimize.model.OptimizationRecommendationTable
import eu.transittrack.schedule.optimize.model.OptimizationRunRepository
import eu.transittrack.schedule.optimize.model.OptimizationRunState
import eu.transittrack.schedule.optimize.model.OptimizationRunTable
import eu.transittrack.support.PostgresPerMethodTest
import eu.transittrack.util.observability.TransitTrackMetrics

/**
 * Drives [OptimizationRetentionScheduler.prune] directly (like [eu.transittrack.predict.PredictionRetentionSchedulerTest])
 * against a real Postgres schema: `schedule_optimization_run.feed_code`/`revision_id` carry no FK
 * constraint (see migration `0007-optimization-runs.yaml`), so rows can be seeded with arbitrary
 * codes/ids without a real feed/revision.
 */
@PostgresSliceTest
class OptimizationRetentionSchedulerTest(
    @Autowired val db: Database,
    @Autowired val runs: OptimizationRunRepository,
) : PostgresPerMethodTest() {
    private val now = Instant.parse("2026-09-13T12:00:00Z")
    private lateinit var scheduler: OptimizationRetentionScheduler

    @BeforeEach
    fun seed() {
        scheduler = OptimizationRetentionScheduler(runs, OptimizationProperties(), TransitTrackMetrics.forTests())

        // Old run (past the 90-day default retention) with a child recommendation that must
        // cascade-delete with it.
        seedRun(1, createdDaysAgo = 100, observedToDaysAgo = 93)
        seedRecommendation(1, runId = 1, reason = "old")

        // Fresh run, retained, with its own recommendation, also retained.
        seedRun(2, createdDaysAgo = 2, observedToDaysAgo = 1)
        seedRecommendation(2, runId = 2, reason = "fresh")
    }

    private fun seedRun(
        runId: Long,
        createdDaysAgo: Long,
        observedToDaysAgo: Long,
    ) = transaction(db) {
        OptimizationRunTable.insert {
            it[id] = runId
            it[feedCode] = "f1"
            it[revisionId] = 1L
            it[observedFrom] = now.minus(createdDaysAgo, ChronoUnit.DAYS)
            it[observedTo] = now.minus(observedToDaysAgo, ChronoUnit.DAYS)
            it[minimumSamples] = 1
            it[state] = OptimizationRunState.SUCCEEDED
            it[createdAt] = now.minus(createdDaysAgo, ChronoUnit.DAYS)
        }
    }

    private fun seedRecommendation(
        recommendationId: Long,
        runId: Long,
        reason: String,
    ) = transaction(db) {
        OptimizationRecommendationTable.insert {
            it[id] = recommendationId
            it[OptimizationRecommendationTable.runId] = runId
            it[kind] = OptimizationRecommendationKind.STOP_TIME
            it[sampleCount] = 5
            it[deltaSec] = 30
            it[OptimizationRecommendationTable.reason] = reason
        }
    }

    private fun ids(
        column: Column<Long>,
        table: Table,
    ): List<Long> = transaction(db) { table.select(column).map { it[column] } }

    @Test
    fun `prunes runs older than the retention window, cascading their recommendations, and retains fresh rows`() {
        val counts = scheduler.prune(now)

        assertThat(counts).isEqualTo(OptimizationRetentionScheduler.PruneCounts(runs = 1))
        assertThat(ids(OptimizationRunTable.id, OptimizationRunTable)).containsExactly(2L)
        assertThat(ids(OptimizationRecommendationTable.id, OptimizationRecommendationTable)).containsExactly(2L)
    }
}
