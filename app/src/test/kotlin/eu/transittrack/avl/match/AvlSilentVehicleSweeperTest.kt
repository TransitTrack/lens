package eu.transittrack.avl.match

import java.time.Instant
import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired

import eu.transittrack.avl.AvlProperties
import eu.transittrack.avl.model.AvlFeedRepository
import eu.transittrack.avl.model.AvlFeedTable
import eu.transittrack.avl.model.AvlReportTable
import eu.transittrack.avl.model.MatchStatus
import eu.transittrack.avl.model.VehicleStateRepository
import eu.transittrack.avl.model.VehicleStateTable
import eu.transittrack.gtfs.support.PostgresSliceTest
import eu.transittrack.support.PostgresPerMethodTest
import eu.transittrack.support.insertAvlFeed
import eu.transittrack.util.observability.TransitTrackMetrics

@PostgresSliceTest
class AvlSilentVehicleSweeperTest(
    @Autowired val db: Database,
    @Autowired val vehicleStates: VehicleStateRepository,
    @Autowired val feeds: AvlFeedRepository,
) : PostgresPerMethodTest() {
    private val now = Instant.parse("2026-09-10T12:00:00Z")
    private val sweeper = AvlSilentVehicleSweeper(vehicleStates, feeds, AvlProperties(), TransitTrackMetrics.forTests())

    /** poll_interval_sec = 15 → stale at max(45, 60) = 60s, unmatch at max(75, 60) = 75s. */
    private fun secondsAgo(s: Long): Instant = now.minusSeconds(s)

    @BeforeEach
    fun seed() {
        db.insertAvlFeed(
            1, "a", name = "A", url = "x", pollIntervalSec = 15,
            assignmentMode = AvlAssignmentMode.FULL_INFERENCE,
        )
    }

    /**
     * Seeds a `vehicle_state` row plus its driving `avl_report` row. Staleness is now judged off the
     * raw report's `ts` ([reportSecondsAgo]), not `vehicle_state.updated_at` — [stateUpdatedSecondsAgo]
     * exists only to populate that NOT NULL column and defaults to the same age as the report so
     * existing test scenarios need no other changes.
     */
    private fun insertMatchedState(
        vehicleId: String,
        reportSecondsAgo: Long,
        stale: Boolean = false,
        stateUpdatedSecondsAgo: Long = reportSecondsAgo,
    ) {
        transaction(db) {
            AvlReportTable.insert {
                it[feedCode] = "a"
                it[AvlReportTable.vehicleId] = vehicleId
                it[ts] = secondsAgo(reportSecondsAgo)
                it[lat] = 44.0
                it[lon] = 26.0
                it[matchStatus] = MatchStatus.MATCHED
                it[createdAt] = secondsAgo(reportSecondsAgo)
            }
            VehicleStateTable.insert {
                it[feedCode] = "a"
                it[VehicleStateTable.vehicleId] = vehicleId
                it[reportTs] = secondsAgo(reportSecondsAgo)
                it[lat] = 44.0
                it[lon] = 26.0
                it[matched] = true
                it[VehicleStateTable.stale] = stale
                it[consecutiveFailures] = 0
                it[revisionId] = 7L
                it[tripRowId] = 8L
                it[blockPk] = 9L
                it[tripPatternId] = 10L
                it[stopPathIndex] = 2
                it[distanceAlongTripM] = 123.0
                it[updatedAt] = secondsAgo(stateUpdatedSecondsAgo)
            }
        }
    }

    private fun state(vehicleId: String): ResultRow =
        transaction(db) { VehicleStateTable.selectAll().where { VehicleStateTable.vehicleId eq vehicleId }.single() }

    @Test
    fun `a vehicle reporting within the stale window is untouched`() {
        insertMatchedState("fresh", reportSecondsAgo = 30)

        assertThat(sweeper.sweep(now)).isEqualTo(AvlSilentVehicleSweeper.SweepCounts(staled = 0, unmatched = 0))

        val s = state("fresh")
        assertThat(s[VehicleStateTable.matched]).isTrue()
        assertThat(s[VehicleStateTable.stale]).isFalse()
        assertThat(s[VehicleStateTable.tripRowId]).isEqualTo(8L)
    }

    @Test
    fun `a vehicle silent past the stale window is flagged but keeps its assignment`() {
        insertMatchedState("stale-only", reportSecondsAgo = 65)

        assertThat(sweeper.sweep(now)).isEqualTo(AvlSilentVehicleSweeper.SweepCounts(staled = 1, unmatched = 0))

        val s = state("stale-only")
        assertThat(s[VehicleStateTable.matched]).isTrue()
        assertThat(s[VehicleStateTable.stale]).isTrue()
        assertThat(s[VehicleStateTable.tripRowId]).isEqualTo(8L)
        assertThat(s[VehicleStateTable.revisionId]).isEqualTo(7L)
    }

    @Test
    fun `a vehicle silent past the unmatch window is unmatched and its assignment dropped`() {
        insertMatchedState("gone", reportSecondsAgo = 120, stale = true)

        assertThat(sweeper.sweep(now)).isEqualTo(AvlSilentVehicleSweeper.SweepCounts(staled = 0, unmatched = 1))

        val s = state("gone")
        assertThat(s[VehicleStateTable.matched]).isFalse()
        assertThat(s[VehicleStateTable.stale]).isTrue()
        assertThat(s[VehicleStateTable.revisionId]).isNull()
        assertThat(s[VehicleStateTable.tripRowId]).isNull()
        assertThat(s[VehicleStateTable.blockPk]).isNull()
        assertThat(s[VehicleStateTable.tripPatternId]).isNull()
        assertThat(s[VehicleStateTable.stopPathIndex]).isNull()
        assertThat(s[VehicleStateTable.distanceAlongTripM]).isNull()
    }

    @Test
    fun `the minimum-seconds floor overrides a tiny poll interval`() {
        transaction(db) { AvlFeedTable.update({ AvlFeedTable.id eq 1L }) { it[pollIntervalSec] = 2 } }
        // 2s * 5 cycles = 10s, but the 60s floor applies → 40s ago is still fresh.
        insertMatchedState("tiny-poll", reportSecondsAgo = 40)

        assertThat(sweeper.sweep(now)).isEqualTo(AvlSilentVehicleSweeper.SweepCounts(staled = 0, unmatched = 0))
        assertThat(state("tiny-poll")[VehicleStateTable.matched]).isTrue()
    }

    @Test
    fun `a second sweep changes nothing`() {
        insertMatchedState("gone", reportSecondsAgo = 200)
        insertMatchedState("staleish", reportSecondsAgo = 65)

        sweeper.sweep(now)
        assertThat(sweeper.sweep(now)).isEqualTo(AvlSilentVehicleSweeper.SweepCounts(staled = 0, unmatched = 0))
    }

    @Test
    fun `a vehicle backlog does not trigger the sweep while its feed still reports`() {
        // our own processor hasn't touched this vehicle_state row in ages, but its feed is still
        // sending fresh avl_report rows for it — this is the exact production bug this fix closes.
        insertMatchedState("backlogged", reportSecondsAgo = 5, stateUpdatedSecondsAgo = 10_000)

        assertThat(sweeper.sweep(now)).isEqualTo(AvlSilentVehicleSweeper.SweepCounts(staled = 0, unmatched = 0))

        val s = state("backlogged")
        assertThat(s[VehicleStateTable.matched]).isTrue()
        assertThat(s[VehicleStateTable.stale]).isFalse()
        assertThat(s[VehicleStateTable.tripRowId]).isEqualTo(8L)
    }
}
