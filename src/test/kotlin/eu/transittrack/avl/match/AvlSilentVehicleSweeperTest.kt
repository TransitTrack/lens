package eu.transittrack.avl.match

import java.sql.Timestamp
import java.time.Instant
import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate

import eu.transittrack.avl.AvlProperties
import eu.transittrack.avl.model.VehicleStateRepository
import eu.transittrack.gtfs.support.PostgresSliceTest

@PostgresSliceTest
class AvlSilentVehicleSweeperTest(
    @Autowired val jdbc: JdbcTemplate,
    @Autowired val vehicleStates: VehicleStateRepository,
) {
    private val now = Instant.parse("2026-09-10T12:00:00Z")
    private val sweeper = AvlSilentVehicleSweeper(vehicleStates, AvlProperties())

    /** poll_interval_sec = 15 → stale at max(45, 60) = 60s, unmatch at max(75, 60) = 75s. */
    private fun secondsAgo(s: Long) = Timestamp.from(now.minusSeconds(s))

    @BeforeEach
    fun seed() {
        jdbc.update(
            "insert into avl_feed (id, code, name, gtfs_feed_code, url, format, poll_interval_sec, " +
                "assignment_mode, enabled, source, created_at, updated_at) " +
                "values (1, 'a', 'A', 'g', 'x', 0, 15, 2, true, 'CONFIG', now(), now())",
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
        jdbc.update(
            "insert into avl_report (feed_id, vehicle_id, ts, lat, lon, match_status, created_at) " +
                "values (1, ?, ?, 44.0, 26.0, 1, ?)",
            vehicleId, secondsAgo(reportSecondsAgo), secondsAgo(reportSecondsAgo),
        )
        jdbc.update(
            "insert into vehicle_state (feed_id, vehicle_id, report_ts, lat, lon, matched, stale, " +
                "consecutive_failures, revision_id, trip_row_id, block_pk, trip_pattern_id, " +
                "stop_path_index, distance_along_trip_m, updated_at) " +
                "values (1, ?, ?, 44.0, 26.0, true, ?, 0, 7, 8, 9, 10, 2, 123.0, ?)",
            vehicleId, secondsAgo(reportSecondsAgo), stale, secondsAgo(stateUpdatedSecondsAgo),
        )
    }

    private fun state(vehicleId: String) = jdbc.queryForMap("select * from vehicle_state where vehicle_id = ?", vehicleId)

    @Test
    fun `a vehicle reporting within the stale window is untouched`() {
        insertMatchedState("fresh", reportSecondsAgo = 30)

        assertThat(sweeper.sweep(now)).isEqualTo(AvlSilentVehicleSweeper.SweepCounts(staled = 0, unmatched = 0))

        val s = state("fresh")
        assertThat(s["matched"] as Boolean).isTrue()
        assertThat(s["stale"] as Boolean).isFalse()
        assertThat(s["trip_row_id"]).isEqualTo(8L)
    }

    @Test
    fun `a vehicle silent past the stale window is flagged but keeps its assignment`() {
        insertMatchedState("stale-only", reportSecondsAgo = 65)

        assertThat(sweeper.sweep(now)).isEqualTo(AvlSilentVehicleSweeper.SweepCounts(staled = 1, unmatched = 0))

        val s = state("stale-only")
        assertThat(s["matched"] as Boolean).isTrue()
        assertThat(s["stale"] as Boolean).isTrue()
        assertThat(s["trip_row_id"]).isEqualTo(8L)
        assertThat(s["revision_id"]).isEqualTo(7L)
    }

    @Test
    fun `a vehicle silent past the unmatch window is unmatched and its assignment dropped`() {
        insertMatchedState("gone", reportSecondsAgo = 120, stale = true)

        assertThat(sweeper.sweep(now)).isEqualTo(AvlSilentVehicleSweeper.SweepCounts(staled = 0, unmatched = 1))

        val s = state("gone")
        assertThat(s["matched"] as Boolean).isFalse()
        assertThat(s["stale"] as Boolean).isTrue()
        assertThat(s["revision_id"]).isNull()
        assertThat(s["trip_row_id"]).isNull()
        assertThat(s["block_pk"]).isNull()
        assertThat(s["trip_pattern_id"]).isNull()
        assertThat(s["stop_path_index"]).isNull()
        assertThat(s["distance_along_trip_m"]).isNull()
    }

    @Test
    fun `the minimum-seconds floor overrides a tiny poll interval`() {
        jdbc.update("update avl_feed set poll_interval_sec = 2 where id = 1")
        // 2s * 5 cycles = 10s, but the 60s floor applies → 40s ago is still fresh.
        insertMatchedState("tiny-poll", reportSecondsAgo = 40)

        assertThat(sweeper.sweep(now)).isEqualTo(AvlSilentVehicleSweeper.SweepCounts(staled = 0, unmatched = 0))
        assertThat(state("tiny-poll")["matched"] as Boolean).isTrue()
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
        assertThat(s["matched"] as Boolean).isTrue()
        assertThat(s["stale"] as Boolean).isFalse()
        assertThat(s["trip_row_id"]).isEqualTo(8L)
    }
}
