package eu.transittrack.schedule.optimize

import java.time.Instant
import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.springframework.beans.factory.annotation.Autowired

import eu.transittrack.gtfs.support.PostgresSliceTest

@PostgresSliceTest
class OptimizationRunPersistenceTest(
    @Autowired private val runs: OptimizationRunRepository,
    @Autowired private val recommendations: OptimizationRecommendationRepository,
) {
    @Test
    fun `persists a queued run with its frozen revision`() {
        val run = runs.save(
            OptimizationRunRow(7, 11, Instant.parse("2026-09-01T00:00:00Z"), Instant.parse("2026-09-08T00:00:00Z"), 20),
        )

        assertThat(runs.findById(run.id!!).orElseThrow().revisionId).isEqualTo(11)

        val recommendation = recommendations.save(
            OptimizationRecommendationRow(run.id!!, OptimizationRecommendationKind.STOP_TIME, 20, 65, "segment evidence"),
        )
        assertThat(recommendations.findById(recommendation.id!!).orElseThrow().deltaSec).isEqualTo(65)
    }
}
