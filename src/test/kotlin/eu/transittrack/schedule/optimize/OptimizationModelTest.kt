package eu.transittrack.schedule.optimize

import java.time.Instant
import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.isEqualTo

class OptimizationModelTest {
    @Test
    fun `new analysis run starts queued against its frozen revision`() {
        val run = OptimizationRun.newRun(
            feedId = 7,
            revisionId = 11,
            observedFrom = Instant.parse("2026-09-01T00:00:00Z"),
            observedTo = Instant.parse("2026-09-08T00:00:00Z"),
            minimumSamples = 20,
        )

        assertThat(run.state).isEqualTo(OptimizationRunState.QUEUED)
        assertThat(run.revisionId).isEqualTo(11)
    }
}
