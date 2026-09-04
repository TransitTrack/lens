package eu.transittrack.avl.ingest

import java.sql.Timestamp
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate

import eu.transittrack.avl.AvlProperties
import eu.transittrack.gtfs.support.PostgresSliceTest

@PostgresSliceTest
class AvlRetentionSchedulerTest(
    @Autowired val jdbc: JdbcTemplate,
) {
    private val now = Instant.parse("2026-09-04T12:00:00Z")

    private fun ago(hours: Long) = Timestamp.from(now.minus(hours, ChronoUnit.HOURS))

    @BeforeEach
    fun seed() {
        jdbc.update(
            "insert into avl_feed (id, code, name, gtfs_feed_code, url, format, poll_interval_sec, " +
                "assignment_mode, enabled, source, created_at, updated_at) " +
                "values (1, 'a', 'A', 'g', 'x', 0, 15, 2, true, 'CONFIG', now(), now())",
        )

        for ((id, age) in listOf(10L to 30L, 11L to 1L)) {
            jdbc.update(
                "insert into avl_report (id, feed_id, vehicle_id, ts, lat, lon, match_status, created_at) " +
                    "values (?, 1, 'v', ?, 44.0, 26.0, 0, ?)",
                id, ago(age), ago(age),
            )
        }

        for ((id, age) in listOf(20L to 100L, 21L to 1L)) {
            jdbc.update(
                "insert into vehicle_match (id, avl_report_id, feed_id, vehicle_id, ts, revision_id, " +
                    "trip_row_id, stop_path_index, distance_along_trip_m, deviation_m, snapped_lat, " +
                    "snapped_lon, created_at) " +
                    "values (?, 11, 1, 'v', ?, 1, 1, 0, 0.0, 0.0, 44.0, 26.0, ?)",
                id, ago(age), ago(age),
            )
        }

        for ((id, age) in listOf(30L to 20L, 31L to 1L)) {
            jdbc.update(
                "insert into vehicle_state (id, feed_id, vehicle_id, report_ts, lat, lon, matched, stale, " +
                    "consecutive_failures, updated_at) " +
                    "values (?, 1, ?, ?, 44.0, 26.0, false, false, 0, ?)",
                id, "v$id", ago(age), ago(age),
            )
        }
    }

    @Test
    fun `prune removes only rows older than the configured windows`() {
        val scheduler = AvlRetentionScheduler(jdbc, AvlProperties())

        val counts = scheduler.prune(now)

        assertThat(counts).isEqualTo(AvlRetentionScheduler.PruneCounts(reports = 1, matches = 1, states = 1))
        assertThat(jdbc.queryForObject("select count(*) from avl_report", Int::class.java)).isEqualTo(1)
        assertThat(jdbc.queryForObject("select count(*) from vehicle_match", Int::class.java)).isEqualTo(1)
        assertThat(jdbc.queryForObject("select count(*) from vehicle_state", Int::class.java)).isEqualTo(1)
    }
}
