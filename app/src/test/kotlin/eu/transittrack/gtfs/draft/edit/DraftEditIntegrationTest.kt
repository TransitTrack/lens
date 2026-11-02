package eu.transittrack.gtfs.draft.edit

import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.json.JsonMapper

import eu.transittrack.gtfs.draft.DraftService
import eu.transittrack.gtfs.model.StopTimeRepository
import eu.transittrack.gtfs.model.StopTimeTable
import eu.transittrack.gtfs.model.TripRepository
import eu.transittrack.gtfs.model.TripTable
import eu.transittrack.gtfs.revision.GtfsRevisionRepository
import eu.transittrack.gtfs.store.RevisionWriter
import eu.transittrack.gtfs.support.IngestionTestFactory
import eu.transittrack.schedule.derive.DerivationService
import eu.transittrack.schedule.derive.ScheduleWriter
import eu.transittrack.schedule.model.ScheduleTimeRepository
import eu.transittrack.schedule.model.ScheduleTimeTable
import eu.transittrack.schedule.model.TripPatternRepository
import eu.transittrack.support.PostgresPerMethodTest

/**
 * End-to-end exercise of the draft-edit template: fork -> claim -> apply four different ops in
 * sequence (each with the version returned by the previous result) -> rederive -> undo the whole
 * chain and prove the raw `stop_times` are byte-for-byte back to the fork state -> redo twice.
 *
 * Like [eu.transittrack.gtfs.draft.DraftServiceTest], the ingestion pieces / `RevisionWriter` commit
 * on their own connections, so this must not run inside a rollback transaction.
 */
@SpringBootTest(classes = [eu.transittrack.Application::class])
@Import(IngestionTestFactory::class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DraftEditIntegrationTest(
    @Autowired val svc: DraftEditService,
    @Autowired val drafts: DraftService,
    @Autowired val derivationService: DerivationService,
    @Autowired val stopTimes: StopTimeRepository,
    @Autowired val trips: TripRepository,
    @Autowired val revisions: GtfsRevisionRepository,
    @Autowired val tripPatterns: TripPatternRepository,
    @Autowired val scheduleTimes: ScheduleTimeRepository,
    @Autowired val writer: RevisionWriter,
    @Autowired val scheduleWriter: ScheduleWriter,
    @Autowired val ingestFactory: IngestionTestFactory,
    @Autowired val json: JsonMapper,
    @Autowired val db: Database,
) : PostgresPerMethodTest() {
    private val clean = mutableListOf<Long>()

    /** Full snapshot of every `stop_times` row for a revision, ignoring the surrogate `id`. */
    private fun stopTimesSnapshot(revisionId: Long): List<Map<String, Any?>> =
        transaction(db) {
            StopTimeTable
                .selectAll()
                .where { StopTimeTable.revisionId eq revisionId }
                .map { row -> StopTimeTable.columns.filter { it != StopTimeTable.id }.associate { it.name to row[it] } }
        }.sortedWith(compareBy({ it["trip_id"] as String }, { it["stop_sequence"] as Int }))

    private fun tripSurrogateId(
        revisionId: Long,
        tripId: String,
    ): Long =
        transaction(db) {
            TripTable
                .selectAll()
                .where { (TripTable.revisionId eq revisionId) and (TripTable.tripId eq tripId) }
                .single()[TripTable.id]
        }

    private fun arrivalsOf(
        revisionId: Long,
        tripId: String,
    ): List<Int?> = stopTimes.findByTripId(revisionId, tripId).map { it.arrivalTime }

    @Test
    fun `edit chain then rederive then undo restores fork state then redo re-applies`() {
        val (feedCode, base) = ingestFactory.ingest("schedule-sample")
        clean += base
        val draft = drafts.fork(feedCode, null, "Integ proposal", "alice")
        val id = draft.id!!
        clean += id
        drafts.claimEditor(id, "alice")

        val forkState = stopTimesSnapshot(id)
        val forkTripCount = trips.findByRevisionId(id).size

        // Derive a full model up front so the "deleted trip loses its derived rows" check is real:
        // capture T3's surrogate id + its schedule_time count now, while it is still a scheduled trip.
        derivationService.rederive(id)
        val t3SurrogateId =
            tripSurrogateId(id, "T3")
        val t1SurrogateId =
            tripSurrogateId(id, "T1")

        fun scheduleRowCount(surrogateId: Long) =
            transaction(db) {
                ScheduleTimeTable
                    .selectAll()
                    .where { (ScheduleTimeTable.revisionId eq id) and (ScheduleTimeTable.tripId eq surrogateId) }
                    .count()
            }
        assertThat(scheduleRowCount(t3SurrogateId) > 0L).isEqualTo(true)
        assertThat(scheduleRowCount(t1SurrogateId) > 0L).isEqualTo(true)

        // T1: all four stops 08:00/08:10/08:20/08:30, arrival == departure.
        val t1Before = arrivalsOf(id, "T1")
        assertThat(t1Before).isEqualTo(listOf(28800, 29400, 30000, 30600))

        // --- edit 1: shift T1 by +120s ---
        val r0 = svc.apply(id, "alice", draft.version) { ShiftTripOp("T1", 120) }
        assertThat(arrivalsOf(id, "T1")).isEqualTo(listOf(28920, 29520, 30120, 30720))
        assertThat(stopTimes.findByTripId(id, "T1").map { it.departureTime })
            .isEqualTo(listOf(28920, 29520, 30120, 30720))

        // --- edit 2: force a 30s dwell at T1 stop_sequence 2 ---
        val r1 = svc.apply(id, "alice", r0.draft.version) { SetStopDwellOp("T1", 2, 30) }
        val dwellRow = stopTimes.findByTripId(id, "T1").single { it.stopSequence == 2 }
        assertThat(dwellRow.departureTime).isEqualTo(dwellRow.arrivalTime!! + 30)
        assertThat(dwellRow.departureTime).isEqualTo(29550)

        // --- edit 3: delete T3 ---
        val r2 = svc.apply(id, "alice", r1.draft.version) { DeleteTripOp("T3") }
        assertThat(trips.findByTripId(id, "T3")).isNull()
        assertThat(stopTimes.findByTripId(id, "T3")).isEmpty()

        // --- edit 4: add a brand-new trip with two dense stop_times ---
        val r3 =
            svc.apply(id, "alice", r2.draft.version) {
                AddTripOp(
                    "RA",
                    "WK",
                    null,
                    "Integ",
                    0,
                    null,
                    null,
                    listOf(NewStopTime("S1", 30000, 30000), NewStopTime("S2", 30300, 30300)),
                )
            }
        val addedTripId =
            transaction(db) {
                TripTable
                    .selectAll()
                    .where { (TripTable.revisionId eq id) and (TripTable.tripHeadsign eq "Integ") }
                    .single()[TripTable.tripId]
            }
        val addedStops = stopTimes.findByTripId(id, addedTripId)
        assertThat(addedStops.map { it.stopSequence }).isEqualTo(listOf(1, 2))
        assertThat(addedStops.map { it.stopId }).isEqualTo(listOf("S1", "S2"))
        assertThat(r3.draft.version).isEqualTo(draft.version + 4)

        // --- rederive the draft ---
        derivationService.rederive(id)
        assertThat(revisions.findById(id)!!.derivationStale).isEqualTo(false)

        // added trip is now a derived trip (has a pattern)
        val addedTrip = trips.findByTripId(id, addedTripId)!!
        assertThat(addedTrip.tripPatternId).isNotNull()

        // deleted trip T3's previously-derived schedule rows are gone; a targeted drop, not a wipe:
        // the other trips (T1) keep their derived rows, and T3's old surrogate id has none.
        assertThat(scheduleRowCount(t3SurrogateId)).isEqualTo(0L)
        assertThat(scheduleRowCount(t1SurrogateId) > 0L).isEqualTo(true)
        assertThat(scheduleTimes.findByRevisionId(id).isNotEmpty()).isEqualTo(true)
        assertThat(tripPatterns.findByRevisionId(id).isNotEmpty()).isEqualTo(true)

        // --- undo the entire chain (four edits) ---
        repeat(4) {
            val v = revisions.findById(id)!!.version
            svc.undo(id, "alice", v)
        }

        val afterUndo = stopTimesSnapshot(id)
        assertThat(afterUndo).isEqualTo(forkState)
        assertThat(trips.findByRevisionId(id).size).isEqualTo(forkTripCount)
        assertThat(trips.findByTripId(id, "T3")).isNotNull()
        assertThat(trips.findByTripId(id, addedTripId)).isNull()
        assertThat(arrivalsOf(id, "T1")).isEqualTo(t1Before)

        // --- redo the first two edits (shift, then dwell) ---
        repeat(2) {
            val v = revisions.findById(id)!!.version
            svc.redo(id, "alice", v)
        }
        assertThat(arrivalsOf(id, "T1")).isEqualTo(listOf(28920, 29520, 30120, 30720))
        val redoneDwell = stopTimes.findByTripId(id, "T1").single { it.stopSequence == 2 }
        assertThat(redoneDwell.departureTime).isEqualTo(redoneDwell.arrivalTime!! + 30)
    }

    @Test
    fun `EditOpBootstrap registers a reversible builder for every op`() {
        val empty = json.createObjectNode()
        val ops =
            listOf(
                "UPDATE_STOP_TIME",
                "SHIFT_TRIP",
                "SET_DWELL",
                "ADD_TRIP",
                "DUPLICATE_TRIP",
                "DELETE_TRIP",
                "BULK_SHIFT",
                "INSERT_TRIP_STOP",
                "REMOVE_TRIP_STOP",
                "REORDER_TRIP_STOPS",
            )
        for (op in ops) {
            val failure = runCatching { EditOpRegistry.mutationFor(op, empty) }.exceptionOrNull()
            assertThat(failure?.message?.contains("no reversible builder") ?: false).isEqualTo(false)
        }
    }
}
