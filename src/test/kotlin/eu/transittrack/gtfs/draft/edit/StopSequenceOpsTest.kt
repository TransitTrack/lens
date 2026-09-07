package eu.transittrack.gtfs.draft.edit

import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertFailsWith

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.AfterEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.json.JsonMapper

import eu.transittrack.TestcontainersConfiguration
import eu.transittrack.gtfs.draft.DraftService
import eu.transittrack.gtfs.model.StopTimeRepository
import eu.transittrack.gtfs.model.Trip
import eu.transittrack.gtfs.model.TripRepository
import eu.transittrack.gtfs.revision.GtfsRevisionRepository
import eu.transittrack.gtfs.store.RevisionWriter
import eu.transittrack.gtfs.support.IngestionTestFactory

@SpringBootTest(classes = [eu.transittrack.Application::class])
@Import(TestcontainersConfiguration::class, IngestionTestFactory::class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class StopSequenceOpsTest(
    @Autowired val svc: DraftEditService,
    @Autowired val drafts: DraftService,
    @Autowired val trips: TripRepository,
    @Autowired val stopTimes: StopTimeRepository,
    @Autowired val revisions: GtfsRevisionRepository,
    @Autowired val writer: RevisionWriter,
    @Autowired val ingestFactory: IngestionTestFactory,
    @Autowired val json: JsonMapper,
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

    private fun pickTripWithStops(draftId: Long): Trip {
        val tid =
            stopTimes
                .findByRevisionId(draftId)
                .groupBy { it.tripId }
                .entries
                .first {
                    it.value.size >= 3 &&
                        it.value
                            .mapNotNull { st -> st.stopId }
                            .toSet()
                            .size == it.value.size
                }.key
        return trips.findByTripId(draftId, tid)!!
    }

    @Test
    fun `EditOpBootstrap registers the stop-sequence ops`() {
        val node = json.createObjectNode().put("tripId", "x")
        node.replace("rows", json.createArrayNode())
        EditOpRegistry.mutationFor("INSERT_TRIP_STOP", node)
        EditOpRegistry.mutationFor("REMOVE_TRIP_STOP", node)
        EditOpRegistry.mutationFor("REORDER_TRIP_STOPS", node)
    }

    @Test
    fun `insert mid-route interpolates, densely renumbers, and undo restores every column`() {
        val draftId = forkDraft()
        val trip = pickTripWithStops(draftId)
        val tripId = trip.tripId
        val rows = stopTimes.findByTripId(draftId, tripId).sortedBy { it.stopSequence }
        // seed a non-time column that a lossy snapshot would drop
        rows[0].stopHeadsign = "Keep me"
        rows[0].timepoint = 1
        stopTimes.save(rows[0])
        val originalSeqs = stopTimes.findByTripId(draftId, tripId).map { it.stopSequence }
        val originalTimes = stopTimes.findByTripId(draftId, tripId).map { it.arrivalTime to it.departureTime }
        val newStopId = rows[0].stopId!!
        val afterSeq = rows[0].stopSequence
        val prevDep = rows[0].departureTime ?: rows[0].arrivalTime!!
        val nextArr = rows[1].arrivalTime ?: rows[1].departureTime!!

        val r1 = svc.apply(draftId, "alice", version(draftId)) { _ ->
            InsertTripStopOp(tripId, afterSeq, newStopId, null, null)
        }

        val after = stopTimes.findByTripId(draftId, tripId).sortedBy { it.stopSequence }
        assertThat(after).hasSize(rows.size + 1)
        assertThat(after.map { it.stopSequence }).isEqualTo((1..rows.size + 1).toList())
        val inserted = after[1]
        assertThat(inserted.stopId).isEqualTo(newStopId)
        assertThat(inserted.arrivalTime).isEqualTo((prevDep + nextArr) / 2)
        assertThat(inserted.departureTime).isEqualTo((prevDep + nextArr) / 2)

        svc.undo(draftId, "alice", r1.draft.version)
        val restored = stopTimes.findByTripId(draftId, tripId).sortedBy { it.stopSequence }
        assertThat(restored.map { it.stopSequence }).isEqualTo(originalSeqs)
        assertThat(restored.map { it.arrivalTime to it.departureTime }).isEqualTo(originalTimes)
        val head = restored.single { it.stopSequence == afterSeq }
        assertThat(head.stopHeadsign).isEqualTo("Keep me")
        assertThat(head.timepoint).isEqualTo(1)
    }

    @Test
    fun `remove drops the row and renumbers 1 to n`() {
        val draftId = forkDraft()
        val tripId = pickTripWithStops(draftId).tripId
        val rows = stopTimes.findByTripId(draftId, tripId).sortedBy { it.stopSequence }
        val originalSeqs = rows.map { it.stopSequence }
        val victim = rows[1].stopSequence

        val r1 = svc.apply(draftId, "alice", version(draftId)) { _ -> RemoveTripStopOp(tripId, victim) }

        val after = stopTimes.findByTripId(draftId, tripId).sortedBy { it.stopSequence }
        assertThat(after).hasSize(rows.size - 1)
        assertThat(after.map { it.stopSequence }).isEqualTo((1..rows.size - 1).toList())

        svc.undo(draftId, "alice", r1.draft.version)
        assertThat(stopTimes.findByTripId(draftId, tripId).map { it.stopSequence }).isEqualTo(originalSeqs)
    }

    @Test
    fun `reorder swaps two stops with their times and rejects a non-permutation`() {
        val draftId = forkDraft()
        val tripId = pickTripWithStops(draftId).tripId
        val seeded = stopTimes.findByTripId(draftId, tripId).sortedBy { it.stopSequence }
        seeded[1].stopHeadsign = "Travels with stop 2"
        seeded[1].pickupType = 3
        stopTimes.save(seeded[1])
        val rows = stopTimes.findByTripId(draftId, tripId).sortedBy { it.stopSequence }
        val ids = rows.mapNotNull { it.stopId }
        val movedStopId = ids[1]
        val timeById = rows.associate { it.stopId to (it.arrivalTime to it.departureTime) }
        val swapped = ids.toMutableList().apply {
            val t = this[0]
            this[0] = this[1]
            this[1] = t
        }

        val r1 = svc.apply(draftId, "alice", version(draftId)) { _ -> ReorderTripStopsOp(tripId, swapped) }

        val after = stopTimes.findByTripId(draftId, tripId).sortedBy { it.stopSequence }
        assertThat(after.map { it.stopId }).isEqualTo(swapped)
        assertThat(after.map { it.stopSequence }).isEqualTo((1..rows.size).toList())
        // times travelled with their stop
        assertThat(after.map { it.stopId to (it.arrivalTime to it.departureTime) }.toMap())
            .isEqualTo(timeById)
        // seeded non-time columns travelled with their stop (now at position 1)
        val movedForward = after.single { it.stopId == movedStopId }
        assertThat(movedForward.stopSequence).isEqualTo(1)
        assertThat(movedForward.stopHeadsign).isEqualTo("Travels with stop 2")
        assertThat(movedForward.pickupType).isEqualTo(3)

        svc.undo(draftId, "alice", r1.draft.version)
        val undone = stopTimes.findByTripId(draftId, tripId).sortedBy { it.stopSequence }
        assertThat(undone.map { it.stopId }).isEqualTo(ids)
        val movedBack = undone.single { it.stopId == movedStopId }
        assertThat(movedBack.stopSequence).isEqualTo(2)
        assertThat(movedBack.stopHeadsign).isEqualTo("Travels with stop 2")
        assertThat(movedBack.pickupType).isEqualTo(3)

        assertFailsWith<IllegalArgumentException> {
            svc.apply(draftId, "alice", version(draftId)) { _ ->
                ReorderTripStopsOp(tripId, ids.dropLast(1))
            }
        }
    }
}
