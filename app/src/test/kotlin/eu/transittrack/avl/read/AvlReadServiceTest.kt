package eu.transittrack.avl.read

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.mockito.kotlin.mock
import org.springframework.beans.factory.annotation.Autowired

import eu.transittrack.avl.ingest.AvlFormat
import eu.transittrack.avl.match.AvlAssignmentMode
import eu.transittrack.avl.match.AvlMatchContextFactory
import eu.transittrack.avl.model.AvlFeed
import eu.transittrack.avl.model.AvlFeedRepository
import eu.transittrack.avl.model.AvlFeedSourceKind
import eu.transittrack.avl.model.AvlReportRow
import eu.transittrack.avl.model.AvlReportRowRepository
import eu.transittrack.avl.model.MatchStatus
import eu.transittrack.avl.model.VehicleMatchRepository
import eu.transittrack.avl.model.VehicleStateRepository
import eu.transittrack.gtfs.support.PostgresSliceTest
import eu.transittrack.support.PostgresPerMethodTest
import eu.transittrack.util.paging.PageRequest

@PostgresSliceTest
class AvlReadServiceTest(
    @Autowired val feeds: AvlFeedRepository,
    @Autowired val reports: AvlReportRowRepository,
    @Autowired val states: VehicleStateRepository,
    @Autowired val matches: VehicleMatchRepository,
) : PostgresPerMethodTest() {
    // No active revision behind the mock, so the report page resolves times in the system zone.
    private val service by lazy { AvlReadService(feeds, states, reports, matches, mock<AvlMatchContextFactory>(), mock(), mock()) }
    private val zone = ZoneId.systemDefault()
    private val day = LocalDate.parse("2026-09-04")

    private fun report(ts: Instant) =
        AvlReportRow(
            feedCode = "f", vehicleId = "v", vehicleLabel = null, ts = ts, lat = 44.0, lon = 26.0, bearing = null,
            speedMps = null, odometerM = null, descTripId = null, descRouteId = null, descDirectionId = null,
            descStartDate = null, descStartTimeSec = null, descScheduleRelationship = null, currentStopSequence = null,
            currentStopId = null, currentStatus = null, occupancyStatus = null, congestionLevel = null,
            matchStatus = MatchStatus.PENDING, matchedAt = null, createdAt = ts,
        )

    /**
     * Page 2 of 10 (0-based `PageRequest(1, 10)`) over 25 reports is reports 11-20 in ascending `ts`
     * order, with the total counting all 25 — as Spring Data's `Page` did.
     */
    @Test
    fun pagingMatchesPreviousSemantics() {
        feeds.save(
            AvlFeed(
                code = "f", name = "F", gtfsFeedCode = "g", url = "http://x", format = AvlFormat.GTFS_RT,
                pollIntervalSec = 15, assignmentMode = AvlAssignmentMode.FULL_INFERENCE, enabled = true, headers = null,
                source = AvlFeedSourceKind.CONFIG, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH,
            ),
        )
        val base = day.atTime(10, 0).atZone(zone).toInstant()
        // Saved out of order so the result's order comes from the query, not insertion.
        val all = (0 until 25).map { base.plusSeconds(10L * it) }
        all.shuffled(kotlin.random.Random(7)).forEach { reports.save(report(it)) }
        val from = day.atTime(LocalTime.parse("09:00")).atZone(zone).toInstant()
        val until = day.atTime(LocalTime.parse("11:00")).atZone(zone).toInstant()

        val page = reports.findReportPage("f", "v", from, until, PageRequest(1, 10))

        assertThat(page.items.map { it.ts }).isEqualTo(all.subList(10, 20))
        assertThat(page.total).isEqualTo(25L)

        val dto = service.avlReportPage("f", "v", day.toString(), "09:00", "11:00", page = 2, pageSize = 10)

        assertThat(dto.items.map { it.ts }).isEqualTo(page.items.map { it.ts.toString() })
        assertThat(dto.items.mapNotNull { it.match }).isEqualTo(emptyList())
        assertThat(dto.totalCount).isEqualTo(25)
        assertThat(dto.page).isEqualTo(2)
        assertThat(dto.pageSize).isEqualTo(10)

        val last = reports.findReportPage("f", "v", from, until, PageRequest(2, 10))
        assertThat(last.items.map { it.ts }).isEqualTo(all.subList(20, 25))
        assertThat(last.total).isEqualTo(25L)
        assertThat(reports.findReportPage("f", "v", from, until, PageRequest(3, 10)).total).isEqualTo(25L)
    }
}
