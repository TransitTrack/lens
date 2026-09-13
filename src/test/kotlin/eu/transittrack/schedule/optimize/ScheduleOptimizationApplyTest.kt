package eu.transittrack.schedule.optimize

import java.time.Instant
import kotlin.test.Test

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import org.junit.jupiter.api.AfterEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.json.JsonMapper

import eu.transittrack.TestcontainersConfiguration
import eu.transittrack.gtfs.draft.DraftEditRepository
import eu.transittrack.gtfs.draft.DraftKind
import eu.transittrack.gtfs.draft.DraftService
import eu.transittrack.gtfs.feed.GtfsFeedRepository
import eu.transittrack.gtfs.model.StopTimeRepository
import eu.transittrack.gtfs.model.TripRepository
import eu.transittrack.gtfs.revision.GtfsRevisionRepository
import eu.transittrack.gtfs.store.RevisionWriter
import eu.transittrack.gtfs.support.IngestionTestFactory
import eu.transittrack.schedule.optimize.model.OptimizationRecommendationKind
import eu.transittrack.schedule.optimize.model.OptimizationRecommendationRepository
import eu.transittrack.schedule.optimize.model.OptimizationRecommendationRow
import eu.transittrack.schedule.optimize.model.OptimizationRecommendationStatus
import eu.transittrack.schedule.optimize.model.OptimizationRunRepository
import eu.transittrack.schedule.optimize.model.OptimizationRunRow
import eu.transittrack.schedule.optimize.model.OptimizationRunState

/**
 * Drives [ScheduleOptimizationService.apply] (design section 7, "Apply workflow") against a real
 * Postgres schema and a genuinely ingested fixture — like [eu.transittrack.gtfs.draft.edit.DraftEditServiceTest],
 * `DraftService.fork`/ingestion commit on their own connections, so this must not run inside a
 * rollback transaction.
 */
@SpringBootTest(classes = [eu.transittrack.Application::class])
@Import(TestcontainersConfiguration::class, IngestionTestFactory::class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ScheduleOptimizationApplyTest(
    @Autowired val service: ScheduleOptimizationService,
    @Autowired val drafts: DraftService,
    @Autowired val feeds: GtfsFeedRepository,
    @Autowired val revisions: GtfsRevisionRepository,
    @Autowired val stopTimes: StopTimeRepository,
    @Autowired val trips: TripRepository,
    @Autowired val edits: DraftEditRepository,
    @Autowired val runs: OptimizationRunRepository,
    @Autowired val recommendations: OptimizationRecommendationRepository,
    @Autowired val writer: RevisionWriter,
    @Autowired val ingestFactory: IngestionTestFactory,
) {
    private val json = JsonMapper.builder().build()
    private val clean = mutableListOf<Long>()

    @AfterEach
    fun c() {
        clean.forEach {
            runCatching { writer.deleteAllForRevision(it) }
            runCatching { revisions.deleteById(it) }
        }
        clean.clear()
    }

    private fun successfulRun(
        feedId: Long,
        revisionId: Long,
    ) = runs.save(
        OptimizationRunRow(
            feedId = feedId,
            revisionId = revisionId,
            observedFrom = Instant.parse("2026-09-01T00:00:00Z"),
            observedTo = Instant.parse("2026-09-08T00:00:00Z"),
            minimumSamples = 1,
            state = OptimizationRunState.SUCCEEDED,
        ),
    )

    private fun stopTimeRecommendation(
        runId: Long,
        tripId: String,
        stopSequence: Int,
        arrivalSec: Int,
        departureSec: Int,
    ) = recommendations.save(
        OptimizationRecommendationRow(
            runId = runId,
            kind = OptimizationRecommendationKind.STOP_TIME,
            sampleCount = 5,
            deltaSec = 30,
            reason = "test",
            proposedValue =
                json.writeValueAsString(
                    linkedMapOf(
                        "targets" to
                            listOf(
                                linkedMapOf(
                                    "tripId" to tripId,
                                    "stopSequence" to stopSequence,
                                    "arrivalSec" to arrivalSec,
                                    "departureSec" to departureSec,
                                ),
                            ),
                    ),
                ),
            conflictKey = "cell:$tripId:$stopSequence",
        ),
    )

    private fun tripShiftRecommendation(
        runId: Long,
        tripId: String,
        deltaSec: Int,
        startTimeSec: Int,
    ) = recommendations.save(
        OptimizationRecommendationRow(
            runId = runId,
            kind = OptimizationRecommendationKind.TRIP_SHIFT,
            sampleCount = 5,
            deltaSec = deltaSec,
            reason = "test",
            proposedValue =
                json.writeValueAsString(
                    linkedMapOf(
                        "targets" to
                            listOf(linkedMapOf("tripId" to tripId, "startTimeSec" to startTimeSec)),
                    ),
                ),
            conflictKey = "shift:$tripId",
        ),
    )

    @Test
    fun `apply forks the frozen revision, claims the editor, journals every op, and marks sources APPLIED`() {
        val (feedCode, base) = ingestFactory.ingest("schedule-sample")
        clean += base
        val feedId = feeds.findByCode(feedCode)!!.id!!

        val sts = stopTimes.findByRevisionId(base)
        val cellTarget = sts.first()
        val shiftTrip = trips.findByRevisionId(base).first { it.tripId != cellTarget.tripId && it.startTimeSec != null }
        val shiftTripOriginalStopTimes = stopTimes.findByTripId(base, shiftTrip.tripId)

        val run = successfulRun(feedId, base)
        val cellRec =
            stopTimeRecommendation(
                run.id!!,
                cellTarget.tripId,
                cellTarget.stopSequence,
                (cellTarget.arrivalTime ?: 0) + 30,
                (cellTarget.departureTime ?: 0) + 30,
            )
        val shiftRec = tripShiftRecommendation(run.id!!, shiftTrip.tripId, 45, shiftTrip.startTimeSec!! + 45)

        val revisionsBefore = revisions.count()

        val updated = service.apply(run.id!!, setOf(cellRec.id!!, shiftRec.id!!), "proposal", "planner")
        clean += updated.id!!

        assertThat(revisions.count()).isEqualTo(revisionsBefore + 1)
        assertThat(updated.kind).isEqualTo(DraftKind.DRAFT)
        assertThat(updated.baseRevisionId).isEqualTo(base)
        assertThat(drafts.currentLock(updated)?.editor).isEqualTo("planner")

        val journal = edits.findByRevisionIdOrderBySeqAsc(updated.id!!)
        assertThat(journal).hasSize(2)
        assertThat(journal.map { it.op }.toSet()).isEqualTo(setOf("UPDATE_STOP_TIME", "SHIFT_TRIP"))

        val updatedCell =
            stopTimes.findByTripId(updated.id!!, cellTarget.tripId).single { it.stopSequence == cellTarget.stopSequence }
        assertThat(updatedCell.arrivalTime).isEqualTo((cellTarget.arrivalTime ?: 0) + 30)
        assertThat(updatedCell.departureTime).isEqualTo((cellTarget.departureTime ?: 0) + 30)

        // ShiftTripOp moves the trip's stop_times (schedule authority), not the derived Trip.startTimeSec.
        val updatedShiftStopTimes = stopTimes.findByTripId(updated.id!!, shiftTrip.tripId)
        val originalByStopSeq = shiftTripOriginalStopTimes.associateBy { it.stopSequence }
        updatedShiftStopTimes.forEach { st ->
            val before = originalByStopSeq.getValue(st.stopSequence)
            if (before.arrivalTime != null) assertThat(st.arrivalTime).isEqualTo(before.arrivalTime!! + 45)
            if (before.departureTime != null) assertThat(st.departureTime).isEqualTo(before.departureTime!! + 45)
        }

        assertThat(recommendations.findAllById(setOf(cellRec.id!!, shiftRec.id!!)).map { it.status }.toSet())
            .isEqualTo(setOf(OptimizationRecommendationStatus.APPLIED))
    }

    @Test
    fun `divergent proposals for the same cell conflict and apply nothing`() {
        val (feedCode, base) = ingestFactory.ingest("schedule-sample")
        clean += base
        val feedId = feeds.findByCode(feedCode)!!.id!!
        val cellTarget = stopTimes.findByRevisionId(base).first()

        val run = successfulRun(feedId, base)
        val recA =
            stopTimeRecommendation(
                run.id!!,
                cellTarget.tripId,
                cellTarget.stopSequence,
                (cellTarget.arrivalTime ?: 0) + 30,
                (cellTarget.departureTime ?: 0) + 30,
            )
        val recB =
            stopTimeRecommendation(
                run.id!!,
                cellTarget.tripId,
                cellTarget.stopSequence,
                (cellTarget.arrivalTime ?: 0) + 60,
                (cellTarget.departureTime ?: 0) + 60,
            )

        val revisionsBefore = revisions.count()

        val ex =
            runCatching { service.apply(run.id!!, setOf(recA.id!!, recB.id!!), "proposal", "planner") }
                .exceptionOrNull()
        assertThat(ex!!).isInstanceOf(RecommendationConflictException::class)
        val conflict = ex as RecommendationConflictException
        assertThat(conflict.conflictingRecommendationIds).contains(recA.id!!)
        assertThat(conflict.conflictingRecommendationIds).contains(recB.id!!)

        assertThat(revisions.count()).isEqualTo(revisionsBefore)
        assertThat(recommendations.findAllById(setOf(recA.id!!, recB.id!!)).map { it.status }.toSet())
            .isEqualTo(setOf(OptimizationRecommendationStatus.PENDING))
    }

    @Test
    fun `a later operation failing rolls back the whole apply - no draft, no journal, no status changes`() {
        val (feedCode, base) = ingestFactory.ingest("schedule-sample")
        clean += base
        val feedId = feeds.findByCode(feedCode)!!.id!!
        val cellTarget = stopTimes.findByRevisionId(base).first()

        val run = successfulRun(feedId, base)
        val valid =
            stopTimeRecommendation(
                run.id!!,
                cellTarget.tripId,
                cellTarget.stopSequence,
                (cellTarget.arrivalTime ?: 0) + 30,
                (cellTarget.departureTime ?: 0) + 30,
            )
        // Targets a stop sequence that does not exist on this trip, so UpdateStopTimeOp.plan()
        // throws once the batch reaches it (`single { ... }` finds no match).
        val invalid = stopTimeRecommendation(run.id!!, cellTarget.tripId, 999_999, 100, 100)

        val revisionsBefore = revisions.count()

        assertFailure {
            service.apply(run.id!!, setOf(valid.id!!, invalid.id!!), "proposal", "planner")
        }

        assertThat(revisions.count()).isEqualTo(revisionsBefore)
        assertThat(recommendations.findAllById(setOf(valid.id!!, invalid.id!!)).map { it.status }.toSet())
            .isEqualTo(setOf(OptimizationRecommendationStatus.PENDING))
        // The stop time targeted by the *valid* op must be untouched too — nothing partial survives.
        assertThat(
            stopTimes.findByTripId(base, cellTarget.tripId).single { it.stopSequence == cellTarget.stopSequence }.arrivalTime,
        ).isEqualTo(cellTarget.arrivalTime)
    }
}
