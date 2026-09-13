package eu.transittrack.schedule.optimize

import java.time.Instant
import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.springframework.beans.factory.annotation.Autowired

import eu.transittrack.gtfs.support.PostgresSliceTest
import eu.transittrack.schedule.optimize.model.OptimizationRecommendationKind
import eu.transittrack.schedule.optimize.model.OptimizationRecommendationRepository
import eu.transittrack.schedule.optimize.model.OptimizationRecommendationRow
import eu.transittrack.schedule.optimize.model.OptimizationRecommendationStatus
import eu.transittrack.schedule.optimize.model.OptimizationRunRepository
import eu.transittrack.schedule.optimize.model.OptimizationRunRow
import eu.transittrack.schedule.optimize.model.OptimizationRunState

@PostgresSliceTest
class OptimizationEntitiesPersistenceTest(
    @Autowired private val runs: OptimizationRunRepository,
    @Autowired private val recommendations: OptimizationRecommendationRepository,
) {
    private fun newRun(revisionId: Long = 11) =
        runs.save(
            OptimizationRunRow(
                feedId = 7,
                revisionId = revisionId,
                serviceId = "weekday",
                routeId = "R1",
                directionId = 0,
                windowFromSec = 6 * 3600,
                windowToSec = 10 * 3600,
                observedFrom = Instant.parse("2026-09-01T00:00:00Z"),
                observedTo = Instant.parse("2026-09-08T00:00:00Z"),
                minimumSamples = 20,
            ),
        )

    @Test
    fun `new run starts queued with its full filters and no lifecycle timestamps`() {
        val run = newRun()

        val loaded = runs.findById(run.id!!).orElseThrow()
        assertThat(loaded.state).isEqualTo(OptimizationRunState.QUEUED)
        assertThat(loaded.revisionId).isEqualTo(11)
        assertThat(loaded.serviceId).isEqualTo("weekday")
        assertThat(loaded.routeId).isEqualTo("R1")
        assertThat(loaded.windowFromSec).isEqualTo(6 * 3600)
        assertThat(loaded.windowToSec).isEqualTo(10 * 3600)
        assertThat(loaded.startedAt).isNull()
        assertThat(loaded.completedAt).isNull()
        assertThat(loaded.error).isNull()
    }

    @Test
    fun `persists lifecycle transitions and a sanitized failure error`() {
        val run = newRun()
        run.state = OptimizationRunState.RUNNING
        run.startedAt = Instant.now()
        runs.save(run)

        run.state = OptimizationRunState.FAILED
        run.completedAt = Instant.now()
        run.error = "optimization run failed unexpectedly; see server logs"
        runs.save(run)

        val loaded = runs.findById(run.id!!).orElseThrow()
        assertThat(loaded.state).isEqualTo(OptimizationRunState.FAILED)
        assertThat(loaded.error).isEqualTo("optimization run failed unexpectedly; see server logs")
    }

    @Test
    fun `persists a recommendation with status, JSON values, and conflict key`() {
        val run = newRun()
        val recommendation =
            recommendations.save(
                OptimizationRecommendationRow(
                    runId = run.id!!,
                    kind = OptimizationRecommendationKind.STOP_TIME,
                    sampleCount = 20,
                    deltaSec = 65,
                    reason = "segment evidence",
                    currentValue = """{"arrivalSec":100}""",
                    proposedValue = """{"arrivalSec":165}""",
                    evidence = """{"sampleCount":20,"medianSec":165}""",
                    conflictKey = "trip:T1/stop:3",
                ),
            )

        val loaded = recommendations.findById(recommendation.id!!).orElseThrow()
        assertThat(loaded.deltaSec).isEqualTo(65)
        assertThat(loaded.status).isEqualTo(OptimizationRecommendationStatus.PENDING)
        assertThat(loaded.currentValue).isEqualTo("""{"arrivalSec":100}""")
        assertThat(loaded.proposedValue).isEqualTo("""{"arrivalSec":165}""")
        assertThat(loaded.evidence).isEqualTo("""{"sampleCount":20,"medianSec":165}""")
        assertThat(loaded.conflictKey).isEqualTo("trip:T1/stop:3")
    }

    @Test
    fun `lists recommendations with stable run id, status, id pagination`() {
        val run = newRun()

        fun recFor(status: OptimizationRecommendationStatus) =
            recommendations.save(
                OptimizationRecommendationRow(
                    runId = run.id!!,
                    kind = OptimizationRecommendationKind.TRIP_SHIFT,
                    sampleCount = 5,
                    deltaSec = 10,
                    reason = "r",
                    status = status,
                ),
            )

        val dismissed = recFor(OptimizationRecommendationStatus.DISMISSED)
        val pendingA = recFor(OptimizationRecommendationStatus.PENDING)
        val pendingB = recFor(OptimizationRecommendationStatus.PENDING)
        val selected = recFor(OptimizationRecommendationStatus.SELECTED)

        // Unfiltered, ordered by status then id: APPLIED < DISMISSED < PENDING < SELECTED < UNAVAILABLE (alphabetical).
        val allPage1 = recommendations.page(run.id!!, null, 0, 3)
        val allPage2 = recommendations.page(run.id!!, null, 3, 3)
        assertThat((allPage1 + allPage2).map { it.id })
            .containsExactly(dismissed.id, pendingA.id, pendingB.id, selected.id)

        val pendingOnly = recommendations.page(run.id!!, OptimizationRecommendationStatus.PENDING.name, 0, 10)
        assertThat(pendingOnly.map { it.id }).containsExactly(pendingA.id, pendingB.id)
    }

    /**
     * `findAllByIdForUpdate` (used by `ScheduleOptimizationService.apply` to serialize concurrent
     * applies of overlapping recommendation selections — see the repository method's doc) must
     * return the exact same rows as the plain `findAllById` it replaces there, proving it is a safe
     * drop-in besides the extra row lock it takes. True concurrent-double-apply is not covered by an
     * automated test: this codebase has no existing Thread/ExecutorService/CountDownLatch pattern for
     * asserting a real serialization race, and a sleep-based test would be flaky, so this is a
     * code-level guarantee (the lock query runs before the PENDING-status check in `applyInternal`
     * and is held for the rest of that `@Transactional` method) rather than an exercised race.
     */
    @Test
    fun `findAllByIdForUpdate returns the same rows as findAllById`() {
        val run = newRun()
        val a =
            recommendations.save(
                OptimizationRecommendationRow(
                    runId = run.id!!,
                    kind = OptimizationRecommendationKind.STOP_TIME,
                    sampleCount = 5,
                    deltaSec = 10,
                    reason = "a",
                ),
            )
        val b =
            recommendations.save(
                OptimizationRecommendationRow(
                    runId = run.id!!,
                    kind = OptimizationRecommendationKind.TRIP_SHIFT,
                    sampleCount = 5,
                    deltaSec = 10,
                    reason = "b",
                ),
            )

        val ids = setOf(a.id!!, b.id!!)
        val viaPlain = recommendations.findAllById(ids).map { it.id }.toSet()
        val viaLocking = recommendations.findAllByIdForUpdate(ids).map { it.id }.toSet()

        assertThat(viaLocking).isEqualTo(viaPlain)
        assertThat(viaLocking).isEqualTo(ids)
    }
}
