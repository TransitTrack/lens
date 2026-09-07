package eu.transittrack.gtfs.draft.edit

import javax.sql.DataSource
import kotlin.test.Test

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotEmpty
import assertk.assertions.messageContains
import org.junit.jupiter.api.AfterEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

import eu.transittrack.TestcontainersConfiguration
import eu.transittrack.gtfs.draft.DraftService
import eu.transittrack.gtfs.model.StopTimeRepository
import eu.transittrack.gtfs.model.TripRepository
import eu.transittrack.gtfs.revision.GtfsRevisionRepository
import eu.transittrack.gtfs.store.RevisionWriter
import eu.transittrack.gtfs.support.IngestionTestFactory
import eu.transittrack.schedule.derive.DerivationService
import eu.transittrack.schedule.derive.ScheduleWriter

@SpringBootTest(classes = [eu.transittrack.Application::class])
@Import(TestcontainersConfiguration::class, IngestionTestFactory::class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class BulkShiftOpTest(
    @Autowired val svc: DraftEditService,
    @Autowired val drafts: DraftService,
    @Autowired val trips: TripRepository,
    @Autowired val stopTimes: StopTimeRepository,
    @Autowired val revisions: GtfsRevisionRepository,
    @Autowired val writer: RevisionWriter,
    @Autowired val scheduleWriter: ScheduleWriter,
    @Autowired val derivation: DerivationService,
    @Autowired val ingestFactory: IngestionTestFactory,
    @Autowired dataSource: DataSource,
) {
    private val jdbc = JdbcTemplate(dataSource)
    private val clean = mutableListOf<Long>()

    @AfterEach
    fun c() {
        clean.forEach { runCatching { scheduleWriter.deleteForRevision(it) } }
        clean.forEach { runCatching { writer.deleteAllForRevision(it) } }
        clean.forEach { runCatching { revisions.deleteById(it) } }
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

    private fun rebuild(draftId: Long) {
        derivation.rederive(draftId)
    }

    private fun version(draftId: Long): Long = revisions.findById(draftId).orElseThrow().version

    private fun minDeparture(
        draftId: Long,
        tripId: String,
    ): Int? = stopTimes.findByTripId(draftId, tripId).mapNotNull { it.departureTime }.minOrNull()

    private fun minTime(
        draftId: Long,
        tripId: String,
    ): Int =
        stopTimes
            .findByTripId(draftId, tripId)
            .flatMap { listOfNotNull(it.arrivalTime, it.departureTime) }
            .minOrNull() ?: 0

    private fun snapshot(draftId: Long): Map<Long, Pair<Int?, Int?>> =
        stopTimes.findByRevisionId(draftId).associate { it.id!! to (it.arrivalTime to it.departureTime) }

    @Test
    fun `bulk shift by route + window moves only matching trips and undo restores all`() {
        val draftId = forkDraft()
        rebuild(draftId)

        val routeId =
            trips
                .findByRevisionId(draftId)
                .groupingBy { it.routeId }
                .eachCount()
                .maxByOrNull { it.value }!!
                .key

        val before = snapshot(draftId)
        val routeTripIds = trips.findByRouteId(draftId, routeId).map { it.tripId }
        val expectedShifted =
            routeTripIds.filter { (minDeparture(draftId, it) ?: Int.MAX_VALUE) in 0..35_999 }
        assertThat(expectedShifted).isNotEmpty()

        val r1 =
            svc.apply(draftId, "alice", version(draftId)) { _ ->
                BulkShiftTripsOp(
                    routeId = routeId,
                    patternKey = null,
                    serviceId = null,
                    windowFromSec = 0,
                    windowToSec = 36_000,
                    deltaSec = 180,
                )
            }

        val after = snapshot(draftId)
        val shiftedTripIds =
            stopTimes
                .findByRevisionId(draftId)
                .filter { after[it.id!!] != before[it.id!!] }
                .map { it.tripId }
                .toSet()
        assertThat(shiftedTripIds).isEqualTo(expectedShifted.toSet())
        expectedShifted.forEach { tid ->
            stopTimes.findByTripId(draftId, tid).forEach { st ->
                val (a0, d0) = before[st.id!!]!!
                assertThat(st.arrivalTime).isEqualTo(a0?.plus(180))
                assertThat(st.departureTime).isEqualTo(d0?.plus(180))
            }
        }

        svc.undo(draftId, "alice", r1.draft.version)
        assertThat(snapshot(draftId)).isEqualTo(before)
    }

    @Test
    fun `pattern filter on a stale draft is rejected`() {
        val draftId = forkDraft()

        assertFailure {
            svc.apply(draftId, "alice", version(draftId)) { _ ->
                BulkShiftTripsOp(
                    routeId = null,
                    patternKey = "x",
                    serviceId = null,
                    windowFromSec = null,
                    windowToSec = null,
                    deltaSec = 60,
                )
            }
        }.isInstanceOf(IllegalStateException::class).messageContains("rebuild")
    }

    @Test
    fun `negative bulk shift clamps per trip and undo lands every trip back`() {
        val draftId = forkDraft()
        rebuild(draftId)

        val before = snapshot(draftId)
        val allTrips = trips.findByRevisionId(draftId).map { it.tripId }
        val earliest = allTrips.minByOrNull { minTime(draftId, it) }!!
        val earliestMin = minTime(draftId, earliest)
        val delta = -(earliestMin + 600) // guaranteed to push the earliest trip below 0

        val r1 =
            svc.apply(draftId, "alice", version(draftId)) { _ ->
                BulkShiftTripsOp(
                    routeId = null,
                    patternKey = null,
                    serviceId = null,
                    windowFromSec = null,
                    windowToSec = null,
                    deltaSec = delta,
                )
            }

        // earliest trip clamped: its new min time is exactly 0
        assertThat(minTime(draftId, earliest)).isEqualTo(0)

        svc.undo(draftId, "alice", r1.draft.version)
        assertThat(snapshot(draftId)).isEqualTo(before)
    }
}
