package eu.transittrack.avl.ingest

import java.time.Instant
import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

import eu.transittrack.avl.model.AvlFeed
import eu.transittrack.avl.model.AvlFeedRepository
import eu.transittrack.avl.model.AvlFeedSourceKind
import eu.transittrack.avl.model.AvlReportRow
import eu.transittrack.avl.model.AvlReportRowRepository
import eu.transittrack.avl.model.MatchStatus
import eu.transittrack.avl.model.VehicleStateRepository
import eu.transittrack.feed.AvlAssignmentMode
import eu.transittrack.feed.AvlFormat
import eu.transittrack.gtfs.support.PostgresSliceTest

/**
 * [AvlWriter] commits through its own [org.hibernate.StatelessSession] transaction, so this test must
 * NOT run inside the default rollback transaction — otherwise the seeded `avl_feed` row (parent of the
 * mandated `avl_report.feed_id` FK) would be invisible to the stateless session's connection. Hence
 * `NOT_SUPPORTED` + explicit cleanup in `@AfterEach`.
 */
@PostgresSliceTest
@Import(AvlWriter::class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AvlWriterTest(
    @Autowired val writer: AvlWriter,
    @Autowired val reports: AvlReportRowRepository,
    @Autowired val states: VehicleStateRepository,
    @Autowired val feeds: AvlFeedRepository,
) {
    private var feedId = 0L

    @BeforeEach
    fun seed() {
        feedId =
            feeds
                .save(
                    AvlFeed(
                        code = "f1", name = "F1", gtfsFeedCode = "g", url = "http://x",
                        format = AvlFormat.GTFS_RT, pollIntervalSec = 30,
                        assignmentMode = AvlAssignmentMode.TRUST_DESCRIPTOR, enabled = true,
                        headers = null, source = AvlFeedSourceKind.CONFIG,
                        createdAt = Instant.now(), updatedAt = Instant.now(),
                    ),
                ).id!!
    }

    @AfterEach
    fun cleanup() {
        reports.deleteAll()
        states.deleteAll()
        feeds.deleteById(feedId)
    }

    private fun report(v: String) =
        AvlReportRow(
            feedId = feedId, vehicleId = v, vehicleLabel = null, ts = Instant.parse("2026-09-04T10:00:00Z"),
            lat = 44.0, lon = 26.0, bearing = null, speedMps = null, odometerM = null, descTripId = null,
            descRouteId = null, descDirectionId = null, descStartDate = null, descStartTimeSec = null,
            descScheduleRelationship = null, currentStopSequence = null, currentStopId = null,
            currentStatus = null, occupancyStatus = null, congestionLevel = null,
            matchStatus = MatchStatus.PENDING, matchedAt = null, createdAt = Instant.parse("2026-09-04T10:00:00Z"),
        )

    private fun upsert(matched: Boolean) =
        VehicleStateUpsert(
            feedId = feedId, vehicleId = "v", vehicleLabel = null, reportTs = Instant.parse("2026-09-04T10:00:00Z"),
            lat = 44.0, lon = 26.0, bearing = null, speedMps = null, occupancyStatus = null,
            matched = matched, stale = !matched, consecutiveFailures = if (matched) 0 else 1,
            revisionId = null, tripRowId = null, blockPk = null, tripPatternId = null, stopPathIndex = null,
            distanceAlongTripM = null, scheduleAdherenceSec = null, snappedLat = null, snappedLon = null,
            updatedAt = Instant.parse("2026-09-04T10:00:00Z"),
        )

    @Test
    fun `batch inserts reports`() {
        writer.insertReports(listOf(report("a"), report("b"), report("c")))
        assertThat(reports.count()).isEqualTo(3L)
    }

    @Test
    fun `upsert replaces the single state row`() {
        writer.upsertState(upsert(matched = false))
        writer.upsertState(upsert(matched = true))
        assertThat(states.findByFeedIdOrderByUpdatedAtDesc(feedId).size).isEqualTo(1)
        assertThat(states.findByFeedIdAndVehicleId(feedId, "v")!!.matched).isEqualTo(true)
    }
}
