package eu.transittrack.avl.model

import java.time.Instant
import jakarta.persistence.EntityManager
import kotlin.test.BeforeTest
import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest

import eu.transittrack.gtfs.support.PostgresSliceTest

@PostgresSliceTest
class AvlEntitiesPersistenceTest(
    @Autowired val reports: AvlReportRowRepository,
    @Autowired val matches: VehicleMatchRepository,
    @Autowired val states: VehicleStateRepository,
    @Autowired val em: EntityManager,
) {
    @BeforeTest
    fun seedFeeds() {
        for (id in 1L..4L) {
            em
                .createNativeQuery(
                    "insert into avl_feed " +
                        "(id, code, name, gtfs_feed_code, url, format, poll_interval_sec, assignment_mode, " +
                        "enabled, source, created_at, updated_at) values " +
                        "(:id, :code, 'feed', 'g', 'http://x', 0, 30, 0, true, 'CONFIG', now(), now())",
                ).setParameter("id", id)
                .setParameter("code", "feed-$id")
                .executeUpdate()
        }
    }

    private fun report(
        feedId: Long,
        vehicle: String,
        ts: Instant,
    ) = AvlReportRow(
        feedId = feedId, vehicleId = vehicle, vehicleLabel = null, ts = ts,
        lat = 44.0, lon = 26.0, bearing = null, speedMps = null, odometerM = null,
        descTripId = null, descRouteId = null, descDirectionId = null, descStartDate = null,
        descStartTimeSec = null, descScheduleRelationship = null, currentStopSequence = null,
        currentStopId = null, currentStatus = null, occupancyStatus = null, congestionLevel = null,
        matchStatus = MatchStatus.PENDING, matchedAt = null, createdAt = ts,
    )

    @Test
    fun `claim batch returns pending ordered`() {
        reports.save(report(1L, "b", Instant.parse("2026-09-04T10:00:02Z")))
        reports.save(report(1L, "a", Instant.parse("2026-09-04T10:00:01Z")))
        val batch = reports.findClaimBatch(10)
        assertThat(batch).hasSize(2)
        assertThat(batch.first().vehicleId).isEqualTo("a")
    }

    @Test
    fun `latest ts per vehicle`() {
        reports.save(report(2L, "x", Instant.parse("2026-09-04T10:00:00Z")))
        reports.save(report(2L, "x", Instant.parse("2026-09-04T10:00:30Z")))
        val latest = reports.latestTsByVehicle(2L).associate { it.vehicleId to it.ts }
        assertThat(latest["x"]).isEqualTo(Instant.parse("2026-09-04T10:00:30Z"))
    }

    @Test
    fun `vehicle_match links a report`() {
        val r = reports.save(report(3L, "v", Instant.parse("2026-09-04T10:00:00Z")))
        matches.save(
            VehicleMatch(
                avlReportId = r.id!!, feedId = 3L, vehicleId = "v", ts = r.ts, revisionId = 9L,
                tripRowId = 7L, blockPk = null, tripPatternId = 4L, stopPathIndex = 2,
                distanceAlongTripM = 120.0, deviationM = 5.0, scheduleAdherenceSec = -30,
                snappedLat = 44.0, snappedLon = 26.0, heading = 90.0, score = 0.9,
                createdAt = r.ts,
            ),
        )
        assertThat(matches.findByFeedIdAndVehicleIdOrderByTsDesc(3L, "v", PageRequest.of(0, 5))).hasSize(1)
    }

    @Test
    fun `vehicle_state unique per feed+vehicle`() {
        states.save(state(4L, "v"))
        assertThat(states.findByFeedIdAndVehicleId(4L, "v")).isEqualTo(states.findByFeedIdAndVehicleId(4L, "v"))
    }

    private fun state(
        feedId: Long,
        vehicle: String,
    ) = VehicleStateRow(
        feedId = feedId, vehicleId = vehicle, vehicleLabel = null,
        reportTs = Instant.parse("2026-09-04T10:00:00Z"), lat = 44.0, lon = 26.0,
        bearing = null, speedMps = null, occupancyStatus = null, matched = false, stale = false,
        consecutiveFailures = 0, revisionId = null, tripRowId = null, blockPk = null,
        tripPatternId = null, stopPathIndex = null, distanceAlongTripM = null,
        scheduleAdherenceSec = null, snappedLat = null, snappedLon = null,
        updatedAt = Instant.parse("2026-09-04T10:00:00Z"),
    )
}
