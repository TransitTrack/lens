package eu.transittrack.avl.ingest

import java.time.Instant
import kotlin.test.Test

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate

import eu.transittrack.avl.match.AvlAssignmentMode
import eu.transittrack.avl.model.AvlFeed
import eu.transittrack.avl.model.AvlFeedRepository
import eu.transittrack.avl.model.AvlFeedSourceKind
import eu.transittrack.avl.model.AvlReportRow
import eu.transittrack.avl.model.AvlReportRowRepository
import eu.transittrack.avl.model.MatchStatus
import eu.transittrack.avl.model.VehicleMatch
import eu.transittrack.avl.model.VehicleMatchRepository
import eu.transittrack.avl.model.VehicleStateRepository
import eu.transittrack.gtfs.support.PostgresSliceTest
import eu.transittrack.support.PostgresPerMethodTest

/**
 * Every [AvlWriter] method commits in its own transaction (`REQUIRES_NEW`), so this test must NOT run
 * inside the default rollback transaction — otherwise the seeded `avl_feed` row (parent of the
 * mandated `avl_report.feed_code` FK) would be invisible to the writer's connection. Hence
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
    @Autowired val matches: VehicleMatchRepository,
    @Autowired val txManager: PlatformTransactionManager,
) : PostgresPerMethodTest() {
    private var feedId = 0L
    private val feedCode = "f1"

    @BeforeEach
    fun seed() {
        feedId =
            feeds
                .save(
                    AvlFeed(
                        code = feedCode, name = "F1", gtfsFeedCode = "g", url = "http://x",
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
            feedCode = feedCode, vehicleId = v, vehicleLabel = null, ts = Instant.parse("2026-09-04T10:00:00Z"),
            lat = 44.0, lon = 26.0, bearing = null, speedMps = null, odometerM = null, descTripId = null,
            descRouteId = null, descDirectionId = null, descStartDate = null, descStartTimeSec = null,
            descScheduleRelationship = null, currentStopSequence = null, currentStopId = null,
            currentStatus = null, occupancyStatus = null, congestionLevel = null,
            matchStatus = MatchStatus.PENDING, matchedAt = null, createdAt = Instant.parse("2026-09-04T10:00:00Z"),
        )

    private fun upsert(
        matched: Boolean,
        vehicleId: String = "v",
    ) = VehicleStateUpsert(
        feedCode = feedCode, vehicleId = vehicleId, vehicleLabel = null, reportTs = Instant.parse("2026-09-04T10:00:00Z"),
        lat = 44.0, lon = 26.0, bearing = null, speedMps = null, occupancyStatus = null,
        matched = matched, stale = !matched, consecutiveFailures = if (matched) 0 else 1,
        revisionId = null, tripRowId = null, blockPk = null, tripPatternId = null, stopPathIndex = null,
        distanceAlongTripM = null, scheduleAdherenceSec = null, snappedLat = null, snappedLon = null,
        updatedAt = Instant.parse("2026-09-04T10:00:00Z"),
    )

    private fun match(report: AvlReportRow) =
        VehicleMatch(
            avlReportId = report.id!!, feedCode = feedCode, vehicleId = report.vehicleId, ts = report.ts,
            revisionId = 1, tripRowId = 1, blockPk = null, tripPatternId = null, stopPathIndex = 0,
            distanceAlongTripM = 0.0, deviationM = 0.0, scheduleAdherenceSec = null, snappedLat = 44.0,
            snappedLon = 26.0, heading = null, score = null, createdAt = report.createdAt,
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
        assertThat(states.findByFeedCodeOrderByUpdatedAtDesc(feedCode).size).isEqualTo(1)
        assertThat(states.findByFeedCodeAndVehicleId(feedCode, "v")!!.matched).isEqualTo(true)
    }

    @Test
    fun upsertStateUpdatesExistingRowInPlace() {
        writer.upsertState(upsert(matched = false))
        val first = states.findByFeedCodeAndVehicleId(feedCode, "v")!!
        val second =
            upsert(matched = true).copy(
                vehicleLabel = "L2", reportTs = Instant.parse("2026-09-04T10:00:30Z"), lat = 45.0, lon = 27.0,
                bearing = 90.0, speedMps = 5.0, occupancyStatus = 2, revisionId = 9, tripRowId = 7, blockPk = 3,
                tripPatternId = 4, stopPathIndex = 2, distanceAlongTripM = 120.0, scheduleAdherenceSec = -30,
                snappedLat = 45.001, snappedLon = 27.001, updatedAt = Instant.parse("2026-09-04T10:00:31Z"),
            )
        writer.upsertState(second)

        val rows = states.findByFeedCodeOrderByUpdatedAtDesc(feedCode)
        assertThat(rows.size).isEqualTo(1)
        val row = rows.single()
        assertThat(row.id).isEqualTo(first.id)
        assertThat(
            VehicleStateUpsert(
                row.feedCode, row.vehicleId, row.vehicleLabel, row.reportTs, row.lat, row.lon, row.bearing, row.speedMps,
                row.occupancyStatus, row.matched, row.stale, row.consecutiveFailures, row.revisionId, row.tripRowId,
                row.blockPk, row.tripPatternId, row.stopPathIndex, row.distanceAlongTripM, row.scheduleAdherenceSec,
                row.snappedLat, row.snappedLon, row.updatedAt,
            ),
        ).isEqualTo(second)
    }

    /** A matching batch can hold several upserts for one vehicle; they apply in list order, last wins. */
    @Test
    fun upsertStatesAppliesRepeatedVehiclesInOrder() {
        writer.upsertStates(listOf(upsert(matched = false), upsert(matched = false, vehicleId = "w"), upsert(matched = true)))

        assertThat(states.findByFeedCodeOrderByUpdatedAtDesc(feedCode).map { it.vehicleId }.sorted()).containsExactly("v", "w")
        assertThat(states.findByFeedCodeAndVehicleId(feedCode, "v")!!.matched).isEqualTo(true)
        assertThat(states.findByFeedCodeAndVehicleId(feedCode, "w")!!.matched).isEqualTo(false)
    }

    // Each AvlWriter method commits on its own (as the stateless-session transaction it replaced did):
    // a caller whose own transaction fails afterwards must not take the write down with it.

    private class CallerFailed : RuntimeException("caller failed")

    private fun inFailingCaller(block: () -> Unit) {
        assertFailure {
            TransactionTemplate(txManager).executeWithoutResult {
                block()
                throw CallerFailed()
            }
        }.isInstanceOf(CallerFailed::class)
    }

    @Test
    fun reportsSurviveCallerFailure() {
        inFailingCaller { writer.insertReports(listOf(report("a"), report("b"))) }
        assertThat(reports.count()).isEqualTo(2L)
    }

    @Test
    fun matchesSurviveCallerFailure() {
        writer.insertReports(listOf(report("a")))
        val saved = reports.findAll().single()
        inFailingCaller { writer.insertMatches(listOf(match(saved))) }
        assertThat(matches.findByAvlReportIdIn(listOf(saved.id!!)).size).isEqualTo(1)
    }

    @Test
    fun statesSurviveCallerFailure() {
        inFailingCaller { writer.upsertState(upsert(matched = true)) }
        inFailingCaller { writer.upsertStates(listOf(upsert(matched = false, vehicleId = "w"))) }
        assertThat(states.findByFeedCodeOrderByUpdatedAtDesc(feedCode).map { it.vehicleId }.sorted()).containsExactly("v", "w")
    }
}
