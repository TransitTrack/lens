package eu.transittrack.avl.ingest

import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired

import eu.transittrack.avl.AvlProperties
import eu.transittrack.avl.match.AvlAssignmentMode
import eu.transittrack.avl.model.AvlReportRowRepository
import eu.transittrack.avl.model.AvlReportTable
import eu.transittrack.avl.model.MatchStatus
import eu.transittrack.avl.model.VehicleMatchRepository
import eu.transittrack.avl.model.VehicleMatchTable
import eu.transittrack.avl.model.VehicleStateRepository
import eu.transittrack.avl.model.VehicleStateTable
import eu.transittrack.gtfs.support.PostgresSliceTest
import eu.transittrack.support.PostgresPerMethodTest
import eu.transittrack.support.insertAvlFeed
import eu.transittrack.util.observability.TransitTrackMetrics

@PostgresSliceTest
class AvlRetentionSchedulerTest(
    @Autowired val db: Database,
    @Autowired val reports: AvlReportRowRepository,
    @Autowired val matches: VehicleMatchRepository,
    @Autowired val states: VehicleStateRepository,
) : PostgresPerMethodTest() {
    private val now = Instant.parse("2026-09-04T12:00:00Z")

    private fun ago(hours: Long): Instant = now.minus(hours, ChronoUnit.HOURS)

    @BeforeEach
    fun seed() {
        db.insertAvlFeed(
            1, "a", name = "A", gtfsFeedCode = "g", url = "x", pollIntervalSec = 15,
            assignmentMode = AvlAssignmentMode.FULL_INFERENCE,
        )

        transaction(db) {
            for ((reportId, age) in listOf(10L to 30L, 11L to 1L)) {
                AvlReportTable.insert {
                    it[id] = reportId
                    it[feedCode] = "a"
                    it[vehicleId] = "v"
                    it[ts] = ago(age)
                    it[lat] = 44.0
                    it[lon] = 26.0
                    it[matchStatus] = MatchStatus.PENDING
                    it[createdAt] = ago(age)
                }
            }

            for ((matchId, age) in listOf(20L to 100L, 21L to 1L)) {
                VehicleMatchTable.insert {
                    it[id] = matchId
                    it[avlReportId] = 11L
                    it[feedCode] = "a"
                    it[vehicleId] = "v"
                    it[ts] = ago(age)
                    it[revisionId] = 1L
                    it[tripRowId] = 1L
                    it[stopPathIndex] = 0
                    it[distanceAlongTripM] = 0.0
                    it[deviationM] = 0.0
                    it[snappedLat] = 44.0
                    it[snappedLon] = 26.0
                    it[createdAt] = ago(age)
                }
            }

            for ((stateId, age) in listOf(30L to 20L, 31L to 1L)) {
                VehicleStateTable.insert {
                    it[id] = stateId
                    it[feedCode] = "a"
                    it[vehicleId] = "v$stateId"
                    it[reportTs] = ago(age)
                    it[lat] = 44.0
                    it[lon] = 26.0
                    it[matched] = false
                    it[stale] = false
                    it[consecutiveFailures] = 0
                    it[updatedAt] = ago(age)
                }
            }
        }
    }

    @Test
    fun `prune removes only rows older than the configured windows`() {
        // Explicit windows: the seed data above is aged 30h/100h/20h, which only exercises pruning
        // with the 24h/72h windows, independent of the production default.
        val props = AvlProperties(
            retention = AvlProperties.Retention(
                reportDuration = Duration.ofHours(24),
                matchDuration = Duration.ofHours(72),
            ),
        )
        val scheduler = AvlRetentionScheduler(reports, matches, states, props, TransitTrackMetrics.forTests())

        val counts = scheduler.prune(now)

        assertThat(counts).isEqualTo(AvlRetentionScheduler.PruneCounts(reports = 1, matches = 1, states = 1))
        assertThat(count(AvlReportTable)).isEqualTo(1L)
        assertThat(count(VehicleMatchTable)).isEqualTo(1L)
        assertThat(count(VehicleStateTable)).isEqualTo(1L)
    }

    private fun count(table: Table): Long = transaction(db) { table.selectAll().count() }
}
