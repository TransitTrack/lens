package eu.transittrack.gtfs.draft.edit

import javax.sql.DataSource
import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.json.JsonMapper

import eu.transittrack.gtfs.draft.DraftService
import eu.transittrack.gtfs.model.StopTime
import eu.transittrack.gtfs.model.StopTimeRepository
import eu.transittrack.gtfs.revision.GtfsRevisionRepository
import eu.transittrack.gtfs.store.RevisionWriter
import eu.transittrack.gtfs.support.IngestionTestFactory
import eu.transittrack.support.PostgresPerMethodTest

/**
 * Like [eu.transittrack.gtfs.draft.edit.DraftEditServiceTest], the ingestion pieces and
 * `RevisionWriter` commit on their own connections, so this must not run inside a rollback tx.
 */
@SpringBootTest(classes = [eu.transittrack.Application::class])
@Import(IngestionTestFactory::class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class TimeEditOpsTest(
    @Autowired val svc: DraftEditService,
    @Autowired val drafts: DraftService,
    @Autowired val stopTimes: StopTimeRepository,
    @Autowired val revisions: GtfsRevisionRepository,
    @Autowired val writer: RevisionWriter,
    @Autowired val ingestFactory: IngestionTestFactory,
    @Autowired val json: JsonMapper,
    @Autowired dataSource: DataSource,
) : PostgresPerMethodTest() {
    private val jdbc = JdbcTemplate(dataSource)
    private val clean = mutableListOf<Long>()

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

    /**
     * F4: no op class is constructed or named in this test, so the only thing that can have
     * populated the registry is `EditOpBootstrap`'s `@PostConstruct` running in the Spring context.
     */
    @Test
    @Order(0)
    fun `EditOpBootstrap registers the time ops at context startup`() {
        val node = json
            .createObjectNode()
            .put("tripId", "x")
            .put("seq", 1)
            .putNull("dep")
        // does not throw -> a reversible builder is registered for each op string
        EditOpRegistry.mutationFor("SET_DWELL", node)
        EditOpRegistry.mutationFor("SHIFT_TRIP", node)
        EditOpRegistry.mutationFor("UPDATE_STOP_TIME", node)
    }

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
        // clamped exactly: the earliest non-null time is now precisely 0 (a >= 0 check would
        // let an off-by-one clamp through)
        val newMin =
            stopTimes
                .findByTripId(draftId, tripId)
                .flatMap { listOfNotNull(it.arrivalTime, it.departureTime) }
                .min()
        assertThat(newMin).isEqualTo(0)

        svc.undo(draftId, "alice", r1.draft.version)
        assertThat(
            stopTimes.findByTripId(draftId, tripId).map { it.arrivalTime to it.departureTime },
        ).isEqualTo(before)
    }

    @Test
    fun `shiftTrip at the clamp boundary applies verbatim and undoes exactly`() {
        val draftId = forkDraft()
        val (tripId, _) = pickTrip(draftId)
        val before = stopTimes.findByTripId(draftId, tripId).map { it.arrivalTime to it.departureTime }
        val minTime = before.flatMap { listOfNotNull(it.first, it.second) }.min()

        val r1 = svc.apply(draftId, "alice", version(draftId)) { _ -> ShiftTripOp(tripId, -minTime) }
        val shifted = stopTimes.findByTripId(draftId, tripId).map { it.arrivalTime to it.departureTime }
        shifted.forEachIndexed { i, (a, d) ->
            assertThat(a).isEqualTo(before[i].first?.minus(minTime))
            assertThat(d).isEqualTo(before[i].second?.minus(minTime))
        }

        svc.undo(draftId, "alice", r1.draft.version)
        assertThat(
            stopTimes.findByTripId(draftId, tripId).map { it.arrivalTime to it.departureTime },
        ).isEqualTo(before)
    }

    @Test
    fun `updateStopTime null null clears both columns then undo restores the numbers`() {
        val draftId = forkDraft()
        val tripId =
            stopTimes
                .findByRevisionId(draftId)
                .groupBy { it.tripId }
                .entries
                .first { e -> e.value.any { it.arrivalTime != null && it.departureTime != null } }
                .key
        val target = stopTimes.findByTripId(draftId, tripId).first { it.arrivalTime != null && it.departureTime != null }
        val seq = target.stopSequence
        val oldArr = target.arrivalTime
        val oldDep = target.departureTime

        val r1 = svc.apply(draftId, "alice", version(draftId)) { _ ->
            UpdateStopTimeOp(tripId, seq, null, null)
        }
        val after = rowAt(draftId, tripId, seq)
        assertThat(after.arrivalTime).isEqualTo(null)
        assertThat(after.departureTime).isEqualTo(null)

        svc.undo(draftId, "alice", r1.draft.version)
        val restored = rowAt(draftId, tripId, seq)
        assertThat(restored.arrivalTime).isEqualTo(oldArr)
        assertThat(restored.departureTime).isEqualTo(oldDep)
    }

    @Test
    fun `updateStopTime on an already-null arrival then undo restores it to null`() {
        val draftId = forkDraft()
        val (tripId, target) = pickTrip(draftId)
        val seq = target.stopSequence
        val dep = target.departureTime
        // prepare: force arrival null
        target.arrivalTime = null
        stopTimes.save(target)

        val r1 = svc.apply(draftId, "alice", version(draftId)) { _ ->
            UpdateStopTimeOp(tripId, seq, 25200, dep)
        }
        assertThat(rowAt(draftId, tripId, seq).arrivalTime).isEqualTo(25200)

        svc.undo(draftId, "alice", r1.draft.version)
        assertThat(rowAt(draftId, tripId, seq).arrivalTime).isEqualTo(null)
    }

    @Test
    fun `setStopDwell with a null departure restores to null on undo`() {
        val draftId = forkDraft()
        val (tripId, target) = pickTrip(draftId)
        val seq = target.stopSequence
        target.departureTime = null
        stopTimes.save(target)
        val arr = rowAt(draftId, tripId, seq).arrivalTime ?: 0

        val r1 = svc.apply(draftId, "alice", version(draftId)) { _ ->
            SetStopDwellOp(tripId, seq, 45)
        }
        assertThat(rowAt(draftId, tripId, seq).departureTime).isEqualTo(arr + 45)

        svc.undo(draftId, "alice", r1.draft.version)
        assertThat(rowAt(draftId, tripId, seq).departureTime).isEqualTo(null)
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
