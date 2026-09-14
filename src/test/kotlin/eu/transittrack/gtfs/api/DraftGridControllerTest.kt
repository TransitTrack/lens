package eu.transittrack.gtfs.api

import javax.sql.DataSource
import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isNotNull
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

import eu.transittrack.gtfs.draft.DraftService
import eu.transittrack.gtfs.feed.GtfsFeedRepository
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
    @Autowired dataSource: DataSource,
) : PostgresPerMethodTest() {
    private val jdbc = JdbcTemplate(dataSource)
    private val cleanupRevs = mutableListOf<Long>()

    @Test
    fun `draftGrid projects raw trips and stop_times for a route`() {
        val (feedCode, activeRev) = ingestFactory.ingest("schedule-sample")
        cleanupRevs += activeRev
        val draft = drafts.fork(feedCode, null, "grid", "alice")
        cleanupRevs += draft.id!!
        val revId = draft.id!!

        val routeId =
            jdbc.queryForObject(
                "select route_id from trips where revision_id = ? group by route_id order by count(*) desc limit 1",
                String::class.java,
                revId,
            )!!

        val expectedTripCount =
            jdbc.queryForObject(
                "select count(*) from trips where revision_id = ? and route_id = ?",
                Long::class.java,
                revId,
                routeId,
            )!!

        val grid = controller.draftGrid(revId.toString(), routeId, null, null)

        assertThat(grid.trips.size.toLong()).isEqualTo(expectedTripCount)
        assertThat(grid.stops.size).isGreaterThan(0)
        assertThat(grid.stops).isEqualTo(grid.stops.sortedBy { it.stopSequence })
        assertThat(grid.trips).isEqualTo(
            grid.trips.sortedWith(compareBy(nullsLast()) { it.firstDepartureSec }),
        )

        val knownTripId = grid.trips.first().tripId
        val expectedCells =
            jdbc.queryForList(
                "select stop_sequence, arrival_time, departure_time from stop_times " +
                    "where revision_id = ? and trip_id = ? order by stop_sequence",
                revId,
                knownTripId,
            )
        val actual = grid.trips.first { it.tripId == knownTripId }.cells
        assertThat(actual.size).isEqualTo(expectedCells.size)
        expectedCells.forEachIndexed { i, row ->
            assertThat(actual[i].stopSequence).isEqualTo((row["stop_sequence"] as Number).toInt())
            assertThat(actual[i].arrivalSec).isEqualTo((row["arrival_time"] as Number?)?.toInt())
            assertThat(actual[i].departureSec).isEqualTo((row["departure_time"] as Number?)?.toInt())
        }

        val firstDep = grid.trips.first { it.tripId == knownTripId }.firstDepartureSec
        if (expectedCells.any { it["departure_time"] != null }) {
            assertThat(firstDep).isNotNull()
        }
    }
}
