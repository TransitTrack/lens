package eu.transittrack.gtfs.draft.edit

import javax.sql.DataSource
import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.AfterEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

import eu.transittrack.TestcontainersConfiguration
import eu.transittrack.gtfs.draft.DraftService
import eu.transittrack.gtfs.model.StopTime
import eu.transittrack.gtfs.model.StopTimeRepository
import eu.transittrack.gtfs.revision.GtfsRevisionRepository
import eu.transittrack.gtfs.store.RevisionWriter
import eu.transittrack.gtfs.support.IngestionTestFactory

/**
 * Like [eu.transittrack.gtfs.draft.edit.DraftEditServiceTest], the ingestion pieces and
 * `RevisionWriter` commit on their own connections, so this must not run inside a rollback tx.
 */
@SpringBootTest(classes = [eu.transittrack.Application::class])
@Import(TestcontainersConfiguration::class, IngestionTestFactory::class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TimeEditOpsTest(
    @Autowired val svc: DraftEditService,
    @Autowired val drafts: DraftService,
    @Autowired val stopTimes: StopTimeRepository,
    @Autowired val revisions: GtfsRevisionRepository,
    @Autowired val writer: RevisionWriter,
    @Autowired val ingestFactory: IngestionTestFactory,
    @Autowired dataSource: DataSource,
) {
    private val jdbc = JdbcTemplate(dataSource)
    private val clean = mutableListOf<Long>()

    @AfterEach
    fun c() {
        clean.forEach {
            runCatching { writer.deleteAllForRevision(it) }
            runCatching { revisions.deleteById(it) }
        }
        clean.clear()
    }

    private fun forkDraft(): Long {
        val (feedCode, base) = ingestFactory.ingest("schedule-sample")
        clean += base
        val draft = drafts.fork(feedCode, null, "T", "alice")
        clean += draft.id!!
        drafts.claimEditor(draft.id!!, "alice")
        return draft.id!!
    }

    private fun version(draftId: Long): Long = revisions.findById(draftId).orElseThrow().version

    /** A trip with at least 3 stops, plus the row at seq index 2 (the 3rd stop). */
    private fun pickTrip(draftId: Long): Pair<String, StopTime> {
        val tripId =
            stopTimes
                .findByRevisionId(draftId)
                .groupBy { it.tripId }
                .entries
                .first { it.value.size >= 3 }
                .key
        val rows = stopTimes.findByTripId(draftId, tripId)
        return tripId to rows[2]
    }

    private fun rowAt(
        draftId: Long,
        tripId: String,
        seq: Int,
    ): StopTime = stopTimes.findByTripId(draftId, tripId).single { it.stopSequence == seq }

    @Test
    fun `updateStopTime sets arr dep then undo restores`() {
        val draftId = forkDraft()
        val (tripId, target) = pickTrip(draftId)
        val seq = target.stopSequence
        val oldArr = target.arrivalTime
        val oldDep = target.departureTime

        val r1 = svc.apply(draftId, "alice", version(draftId)) { _ ->
            UpdateStopTimeOp(tripId, seq, 25200, 25260)
        }
        val after = rowAt(draftId, tripId, seq)
        assertThat(after.arrivalTime).isEqualTo(25200)
        assertThat(after.departureTime).isEqualTo(25260)

        svc.undo(draftId, "alice", r1.draft.version)
        val restored = rowAt(draftId, tripId, seq)
        assertThat(restored.arrivalTime).isEqualTo(oldArr)
        assertThat(restored.departureTime).isEqualTo(oldDep)
    }

    @Test
    fun `shiftTrip plus 120 shifts all times then undo restores`() {
        val draftId = forkDraft()
        val (tripId, _) = pickTrip(draftId)
        val before = stopTimes.findByTripId(draftId, tripId).map { it.arrivalTime to it.departureTime }

        val r1 = svc.apply(draftId, "alice", version(draftId)) { _ -> ShiftTripOp(tripId, 120) }
        val shifted = stopTimes.findByTripId(draftId, tripId).map { it.arrivalTime to it.departureTime }
        shifted.forEachIndexed { i, (a, d) ->
            assertThat(a).isEqualTo(before[i].first?.plus(120))
            assertThat(d).isEqualTo(before[i].second?.plus(120))
        }

        svc.undo(draftId, "alice", r1.draft.version)
        assertThat(
            stopTimes.findByTripId(draftId, tripId).map { it.arrivalTime to it.departureTime },
        ).isEqualTo(before)
    }

    @Test
    fun `shiftTrip negative past zero clamps and undo lands exactly back`() {
        val draftId = forkDraft()
        val (tripId, _) = pickTrip(draftId)
        val before = stopTimes.findByTripId(draftId, tripId).map { it.arrivalTime to it.departureTime }
        val minTime = before.flatMap { listOfNotNull(it.first, it.second) }.min()

        val r1 = svc.apply(draftId, "alice", version(draftId)) { _ ->
            ShiftTripOp(tripId, -(minTime + 100_000))
        }
        // clamped: nothing negative
        stopTimes.findByTripId(draftId, tripId).forEach {
            it.arrivalTime?.let { v -> assertThat(v >= 0).isEqualTo(true) }
            it.departureTime?.let { v -> assertThat(v >= 0).isEqualTo(true) }
        }

        svc.undo(draftId, "alice", r1.draft.version)
        assertThat(
            stopTimes.findByTripId(draftId, tripId).map { it.arrivalTime to it.departureTime },
        ).isEqualTo(before)
    }

    @Test
    fun `setStopDwell sets departure to arrival plus dwell then undo restores`() {
        val draftId = forkDraft()
        val (tripId, target) = pickTrip(draftId)
        val seq = target.stopSequence
        val oldDep = target.departureTime
        val arr = target.arrivalTime ?: oldDep ?: 0

        val r1 = svc.apply(draftId, "alice", version(draftId)) { _ ->
            SetStopDwellOp(tripId, seq, 45)
        }
        assertThat(rowAt(draftId, tripId, seq).departureTime).isEqualTo(arr + 45)

        svc.undo(draftId, "alice", r1.draft.version)
        assertThat(rowAt(draftId, tripId, seq).departureTime).isEqualTo(oldDep)
    }
}
