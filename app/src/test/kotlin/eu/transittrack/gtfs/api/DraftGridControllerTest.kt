package eu.transittrack.gtfs.api

import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isNotNull
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

import eu.transittrack.gtfs.draft.DraftService
import eu.transittrack.gtfs.feed.GtfsFeedRepository
import eu.transittrack.gtfs.model.StopTimeTable
import eu.transittrack.gtfs.model.TripTable
import eu.transittrack.gtfs.revision.GtfsRevisionRepository
import eu.transittrack.gtfs.store.RevisionWriter
import eu.transittrack.gtfs.support.IngestionTestFactory
import eu.transittrack.schedule.derive.ScheduleWriter
import eu.transittrack.support.PostgresPerMethodTest

@SpringBootTest(classes = [eu.transittrack.Application::class])
@Import(IngestionTestFactory::class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DraftGridControllerTest(
    @Autowired val controller: DraftGridController,
    @Autowired val drafts: DraftService,
    @Autowired val revisions: GtfsRevisionRepository,
    @Autowired val feeds: GtfsFeedRepository,
    @Autowired val writer: RevisionWriter,
    @Autowired val scheduleWriter: ScheduleWriter,
    @Autowired val ingestFactory: IngestionTestFactory,
    @Autowired val db: Database,
) : PostgresPerMethodTest() {
    private val cleanupRevs = mutableListOf<Long>()

    @Test
    fun `draftGrid projects raw trips and stop_times for a route`() {
        val (feedCode, activeRev) = ingestFactory.ingest("schedule-sample")
        cleanupRevs += activeRev
        val draft = drafts.fork(feedCode, null, "grid", "alice")
        cleanupRevs += draft.id!!
        val revId = draft.id!!

        val routeId =
            transaction(db) {
                val tripCount = TripTable.id.count()
                TripTable
                    .select(TripTable.routeId, tripCount)
                    .where { TripTable.revisionId eq revId }
                    .groupBy(TripTable.routeId)
                    .orderBy(tripCount, SortOrder.DESC)
                    .limit(1)
                    .single()[TripTable.routeId]
            }

        val expectedTripCount =
            transaction(db) {
                TripTable.selectAll().where { (TripTable.revisionId eq revId) and (TripTable.routeId eq routeId) }.count()
            }

        val grid = controller.draftGrid(revId.toString(), routeId, null, null)

        assertThat(grid.trips.size.toLong()).isEqualTo(expectedTripCount)
        assertThat(grid.stops.size).isGreaterThan(0)
        assertThat(grid.stops).isEqualTo(grid.stops.sortedBy { it.stopSequence })
        assertThat(grid.trips).isEqualTo(
            grid.trips.sortedWith(compareBy(nullsLast()) { it.firstDepartureSec }),
        )

        val knownTripId = grid.trips.first().tripId
        val expectedCells =
            transaction(db) {
                StopTimeTable
                    .selectAll()
                    .where { (StopTimeTable.revisionId eq revId) and (StopTimeTable.tripId eq knownTripId) }
                    .orderBy(StopTimeTable.stopSequence)
                    .toList()
            }
        val actual = grid.trips.first { it.tripId == knownTripId }.cells
        assertThat(actual.size).isEqualTo(expectedCells.size)
        expectedCells.forEachIndexed { i, row ->
            assertThat(actual[i].stopSequence).isEqualTo(row[StopTimeTable.stopSequence])
            assertThat(actual[i].arrivalSec).isEqualTo(row[StopTimeTable.arrivalTime])
            assertThat(actual[i].departureSec).isEqualTo(row[StopTimeTable.departureTime])
        }

        val firstDep = grid.trips.first { it.tripId == knownTripId }.firstDepartureSec
        if (expectedCells.any { it[StopTimeTable.departureTime] != null }) {
            assertThat(firstDep).isNotNull()
        }
    }
}
