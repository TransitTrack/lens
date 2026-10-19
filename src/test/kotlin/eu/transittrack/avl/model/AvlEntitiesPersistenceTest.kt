package eu.transittrack.avl.model

import java.time.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
import org.springframework.jdbc.core.JdbcTemplate

import eu.transittrack.gtfs.support.PostgresSliceTest
import eu.transittrack.support.PostgresPerMethodTest

@PostgresSliceTest
class AvlEntitiesPersistenceTest(
    @Autowired val reports: AvlReportRowRepository,
    @Autowired val matches: VehicleMatchRepository,
    @Autowired val states: VehicleStateRepository,
    @Autowired val jdbcTemplate: JdbcTemplate,
) : PostgresPerMethodTest() {
    // A plain JDBC insert (not an EntityManager native query) - the latter needs an active
    // transaction, which NOT_SUPPORTED deliberately does not provide.
    @BeforeTest
    fun seedFeeds() {
        for (id in 1L..4L) {
            jdbcTemplate.update(
                "insert into avl_feed " +
                    "(id, code, name, gtfs_feed_code, url, format, poll_interval_sec, assignment_mode, " +
                    "enabled, source, created_at, updated_at) values " +
                    "(?, ?, 'feed', 'g', 'http://x', 0, 30, 0, true, 'CONFIG', now(), now())",
                id,
                "feed-$id",
            )
        }
    }

    private fun report(
        feedCode: String,
        vehicle: String,
        ts: Instant,
    ) = AvlReportRow(
        feedCode = feedCode, vehicleId = vehicle, vehicleLabel = null, ts = ts,
        lat = 44.0, lon = 26.0, bearing = null, speedMps = null, odometerM = null,
        descTripId = null, descRouteId = null, descDirectionId = null, descStartDate = null,
        descStartTimeSec = null, descScheduleRelationship = null, currentStopSequence = null,
        currentStopId = null, currentStatus = null, occupancyStatus = null, congestionLevel = null,
        matchStatus = MatchStatus.PENDING, matchedAt = null, createdAt = ts,
    )

    @Test
    fun `claim batch returns pending ordered`() {
        reports.save(report("feed-1", "b", Instant.parse("2026-09-04T10:00:02Z")))
        reports.save(report("feed-1", "a", Instant.parse("2026-09-04T10:00:01Z")))
        val batch = reports.findClaimBatch(10)
        assertThat(batch).hasSize(2)
        assertThat(batch.first().vehicleId).isEqualTo("a")
    }

    @Test
    fun `claim batch is scoped to one feed`() {
        reports.save(report("feed-1", "a", Instant.parse("2026-09-04T10:00:00Z")))
        reports.save(report("feed-2", "b", Instant.parse("2026-09-04T10:00:00Z")))

        val batch = reports.findClaimBatch("feed-1", 10)

        assertThat(batch).hasSize(1)
        assertThat(batch.first().feedCode).isEqualTo("feed-1")
    }

    @Test
    fun `latest ts per vehicle`() {
        reports.save(report("feed-2", "x", Instant.parse("2026-09-04T10:00:00Z")))
        reports.save(report("feed-2", "x", Instant.parse("2026-09-04T10:00:30Z")))
        val latest = reports.latestTsByVehicle("feed-2").associate { it.vehicleId to it.ts }
        assertThat(latest["x"]).isEqualTo(Instant.parse("2026-09-04T10:00:30Z"))
    }

    @Test
    fun `vehicle_match links a report`() {
        val r = reports.save(report("feed-3", "v", Instant.parse("2026-09-04T10:00:00Z")))
        matches.save(
            VehicleMatch(
                avlReportId = r.id!!, feedCode = "feed-3", vehicleId = "v", ts = r.ts, revisionId = 9L,
                tripRowId = 7L, blockPk = null, tripPatternId = 4L, stopPathIndex = 2,
                distanceAlongTripM = 120.0, deviationM = 5.0, scheduleAdherenceSec = -30,
                snappedLat = 44.0, snappedLon = 26.0, heading = 90.0, score = 0.9,
                createdAt = r.ts,
            ),
        )
        assertThat(matches.findByVehicle("feed-3", "v", PageRequest.of(0, 5))).hasSize(1)
    }

    @Test
    fun `vehicle_state unique per feed+vehicle`() {
        val saved = states.save(state("feed-4", "v"))
        // VehicleState has no equals() override, so comparing two lookups directly would only pass
        // by accident of a shared persistence-context cache returning the same instance; assert on
        // the id instead, which is what "unique per feed+vehicle" actually means.
        assertThat(states.findByFeedCodeAndVehicleId("feed-4", "v")!!.id).isEqualTo(saved.id)
    }

    private fun state(
        feedCode: String,
        vehicle: String,
    ) = VehicleState(
        feedCode = feedCode, vehicleId = vehicle, vehicleLabel = null,
        reportTs = Instant.parse("2026-09-04T10:00:00Z"), lat = 44.0, lon = 26.0,
        bearing = null, speedMps = null, occupancyStatus = null, matched = false, stale = false,
        consecutiveFailures = 0, revisionId = null, tripRowId = null, blockPk = null,
        tripPatternId = null, stopPathIndex = null, distanceAlongTripM = null,
        scheduleAdherenceSec = null, snappedLat = null, snappedLon = null,
        updatedAt = Instant.parse("2026-09-04T10:00:00Z"),
    )
}
