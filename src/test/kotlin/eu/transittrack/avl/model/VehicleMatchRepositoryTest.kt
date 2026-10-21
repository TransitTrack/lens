package eu.transittrack.avl.model

import java.time.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate

import eu.transittrack.gtfs.support.PostgresSliceTest
import eu.transittrack.support.PostgresPerMethodTest

@PostgresSliceTest
class VehicleMatchRepositoryTest(
    @Autowired val reports: AvlReportRowRepository,
    @Autowired val matches: VehicleMatchRepository,
    @Autowired val jdbcTemplate: JdbcTemplate,
) : PostgresPerMethodTest() {
    // A plain JDBC insert (not an EntityManager native query) - the latter needs an active
    // transaction, which NOT_SUPPORTED deliberately does not provide.
    @BeforeTest
    fun seedFeeds() {
        jdbcTemplate.update(
            "insert into avl_feed " +
                "(id, code, name, gtfs_feed_code, url, format, poll_interval_sec, assignment_mode, " +
                "enabled, source, created_at, updated_at) values " +
                "(1, 'feed-1', 'feed', 'g', 'http://x', 0, 30, 0, true, 'CONFIG', now(), now())",
        )
    }

    private fun report(
        feedCode: String,
        vehicle: String,
        ts: Instant,
    ) = reports.save(
        AvlReportRow(
            feedCode = feedCode, vehicleId = vehicle, vehicleLabel = null, ts = ts,
            lat = 44.0, lon = 26.0, bearing = null, speedMps = null, odometerM = null,
            descTripId = null, descRouteId = null, descDirectionId = null, descStartDate = null,
            descStartTimeSec = null, descScheduleRelationship = null, currentStopSequence = null,
            currentStopId = null, currentStatus = null, occupancyStatus = null, congestionLevel = null,
            matchStatus = MatchStatus.PENDING, matchedAt = null, createdAt = ts,
        ),
    )

    private fun match(
        feedCode: String,
        vehicle: String,
        ts: Instant,
        tripRowId: Long,
    ): VehicleMatch {
        val r = report(feedCode, vehicle, ts)
        return matches.save(
            VehicleMatch(
                avlReportId = r.id!!, feedCode = feedCode, vehicleId = vehicle, ts = ts, revisionId = 9L,
                tripRowId = tripRowId, blockPk = null, tripPatternId = 4L, stopPathIndex = 2,
                distanceAlongTripM = 120.0, deviationM = 5.0, scheduleAdherenceSec = -30,
                snappedLat = 44.0, snappedLon = 26.0, heading = 90.0, score = 0.9,
                createdAt = ts,
            ),
        )
    }

    @Test
    fun `findByFeedCodeAndTsBetween returns matches in range ordered by vehicle then time`() {
        val from = Instant.parse("2026-09-04T10:00:00Z")
        val until = Instant.parse("2026-09-04T11:00:00Z")

        match("feed-1", "v", Instant.parse("2026-09-04T09:00:00Z"), 1L) // before `from`
        // Sort keys deliberately disagree at both levels:
        //  - "b" (10:15) vs "a" (...): a ts-only order would put "b" before any "a" row with a
        //    later ts, but the correct `vehicleId` primary key must put all "a" rows first.
        //  - two rows for "a" itself, inserted in reverse-ts order (10:30 saved before 10:10):
        //    only a real `ts` secondary sort (not insertion/natural order) puts 10:10 before
        //    10:30 within that vehicle.
        val b = match("feed-1", "b", Instant.parse("2026-09-04T10:15:00Z"), 1L)
        val aLate = match("feed-1", "a", Instant.parse("2026-09-04T10:30:00Z"), 2L)
        val aEarly = match("feed-1", "a", Instant.parse("2026-09-04T10:10:00Z"), 2L)

        val result = matches.findByFeedCodeAndTsBetween("feed-1", from, until)

        assertThat(result).hasSize(3)
        assertThat(result.map { it.id }).isEqualTo(listOf(aEarly.id, aLate.id, b.id))
    }

    @Test
    fun `findByFeedCodeAndTripRowIdInAndTsBetween filters by trip row id`() {
        val from = Instant.parse("2026-09-04T10:00:00Z")
        val until = Instant.parse("2026-09-04T11:00:00Z")

        val tripRowIdA = 1L
        match("feed-1", "a", Instant.parse("2026-09-04T10:10:00Z"), tripRowIdA)
        match("feed-1", "b", Instant.parse("2026-09-04T10:20:00Z"), 2L)

        val result = matches.findByFeedCodeAndTripRowIdInAndTsBetween("feed-1", setOf(tripRowIdA), from, until)

        assertThat(result).hasSize(1)
        assertThat(result.single().tripRowId).isEqualTo(tripRowIdA)
    }
}
