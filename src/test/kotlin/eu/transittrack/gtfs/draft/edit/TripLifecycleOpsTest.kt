package eu.transittrack.gtfs.draft.edit

import javax.sql.DataSource
import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
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
class TripLifecycleOpsTest(
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
                .first { it.value.size >= 3 }
                .key
        return trips.findByTripId(draftId, tid)!!
    }

    @Test
    fun `EditOpBootstrap registers the trip lifecycle ops at context startup`() {
        val node = json.createObjectNode().put("tripId", "x")
        EditOpRegistry.mutationFor("ADD_TRIP", node)
        EditOpRegistry.mutationFor("DUPLICATE_TRIP", node)
        EditOpRegistry.mutationFor("DELETE_TRIP", node)
    }

    @Test
    fun `deleteTrip removes trip and stop_times then undo restores every column`() {
        val draftId = forkDraft()
        val trip = pickTripWithStops(draftId)
        val tripId = trip.tripId

        // seed non-trivial columns that a lossy snapshot would drop
        trip.wheelchairAccessible = 1
        trips.save(trip)
        val rows = stopTimes.findByTripId(draftId, tripId)
        val markedSeq = rows[1].stopSequence
        rows[1].stopHeadsign = "Special headsign"
        rows[1].pickupType = 2
        stopTimes.save(rows[1])
        val originalCount = rows.size
        val originalTimes = stopTimes.findByTripId(draftId, tripId).map { it.arrivalTime to it.departureTime }

        val r1 = svc.apply(draftId, "alice", version(draftId)) { _ -> DeleteTripOp(tripId) }
        assertThat(trips.findByTripId(draftId, tripId)).isNull()
        assertThat(stopTimes.findByTripId(draftId, tripId)).isEmpty()

        svc.undo(draftId, "alice", r1.draft.version)
        val restored = trips.findByTripId(draftId, tripId)
        assertThat(restored).isNotNull()
        assertThat(restored!!.wheelchairAccessible).isEqualTo(1)
        val restoredRows = stopTimes.findByTripId(draftId, tripId)
        assertThat(restoredRows).hasSize(originalCount)
        assertThat(restoredRows.map { it.arrivalTime to it.departureTime }).isEqualTo(originalTimes)
        val markedRow = restoredRows.single { it.stopSequence == markedSeq }
        assertThat(markedRow.stopHeadsign).isEqualTo("Special headsign")
        assertThat(markedRow.pickupType).isEqualTo(2)
    }

    @Test
    fun `addTrip creates a trip with dense stop_sequence then undo deletes it`() {
        val draftId = forkDraft()
        val src = pickTripWithStops(draftId)
        val stopId = stopTimes.findByTripId(draftId, src.tripId).first().stopId!!

        val newStops =
            listOf(
                NewStopTime(stopId, 30_000, 30_030),
                NewStopTime(stopId, 30_300, 30_330),
                NewStopTime(stopId, 30_600, 30_600),
            )
        val r1 = svc.apply(draftId, "alice", version(draftId)) { _ ->
            AddTripOp(src.routeId, src.serviceId, "brand_new_trip", "Test dest", 0, null, null, newStops)
        }

        val created = trips.findByTripId(draftId, "brand_new_trip")
        assertThat(created).isNotNull()
        val createdRows = stopTimes.findByTripId(draftId, "brand_new_trip")
        assertThat(createdRows).hasSize(3)
        assertThat(createdRows.map { it.stopSequence }).isEqualTo(listOf(1, 2, 3))
        assertThat(createdRows[0].arrivalTime).isEqualTo(30_000)
        assertThat(createdRows[2].departureTime).isEqualTo(30_600)

        svc.undo(draftId, "alice", r1.draft.version)
        assertThat(trips.findByTripId(draftId, "brand_new_trip")).isNull()
        assertThat(stopTimes.findByTripId(draftId, "brand_new_trip")).isEmpty()
    }

    @Test
    fun `duplicateTrip offsets times and auto-suffixes the id when taken`() {
        val draftId = forkDraft()
        val src = pickTripWithStops(draftId)
        val srcTimes = stopTimes.findByTripId(draftId, src.tripId).map { it.arrivalTime to it.departureTime }
        val offset = 3_600

        // first copy: no id requested -> "<source>_copy"
        svc.apply(draftId, "alice", version(draftId)) { _ ->
            DuplicateTripOp(src.tripId, null, offset)
        }
        val copyId = "${src.tripId}_copy"
        assertThat(trips.findByTripId(draftId, copyId)).isNotNull()
        val copyTimes = stopTimes.findByTripId(draftId, copyId).map { it.arrivalTime to it.departureTime }
        assertThat(copyTimes).isEqualTo(
            srcTimes.map { (a, d) -> a?.plus(offset) to d?.plus(offset) },
        )

        // second copy: requested id already exists -> "<source>_copy2"
        val r2 = svc.apply(draftId, "alice", version(draftId)) { _ ->
            DuplicateTripOp(src.tripId, src.tripId, offset)
        }
        val copy2Id = "${src.tripId}_copy2"
        assertThat(trips.findByTripId(draftId, copy2Id)).isNotNull()

        svc.undo(draftId, "alice", r2.draft.version)
        assertThat(trips.findByTripId(draftId, copy2Id)).isNull()
        assertThat(stopTimes.findByTripId(draftId, copy2Id)).isEmpty()
        // first copy survives the second's undo
        assertThat(trips.findByTripId(draftId, copyId)).isNotNull()
    }
}
