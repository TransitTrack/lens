package eu.transittrack.predict

import java.sql.Timestamp
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate

import eu.transittrack.gtfs.support.PostgresSliceTest
import eu.transittrack.support.PostgresPerMethodTest

@PostgresSliceTest
class PredictionRetentionSchedulerTest(
    @Autowired val jdbc: JdbcTemplate,
) : PostgresPerMethodTest() {
    private val now = Instant.parse("2026-09-05T12:00:00Z")
    private lateinit var scheduler: PredictionRetentionScheduler

    @BeforeEach
    fun seed() {
        scheduler = PredictionRetentionScheduler(jdbc, PredictProperties())

        jdbc.update(
            "insert into gtfs_feed (id, code, name, url, enabled, source, created_at, updated_at) " +
                "values (1, 'g', 'G', 'x', true, 'CONFIG', now(), now())",
        )
        jdbc.update(
            "insert into gtfs_revision (id, feed_id, status, source_url, created_at) " +
                "values (1, 1, 'ACTIVE', 'x', now())",
        )
        jdbc.update(
            "insert into trip_patterns (id, revision_id, pattern_key, route_id, stop_count, trip_count) " +
                "values (100, 1, 'k', 'r', 1, 1)",
        )
        jdbc.update(
            "insert into avl_feed (id, code, name, gtfs_feed_code, url, format, poll_interval_sec, " +
                "assignment_mode, enabled, source, prediction_algorithm, prediction_mode, created_at, updated_at) " +
                "values (1, 'f1', 'F1', 'g', 'http://x', 0, 30, 0, true, 'CONFIG', 0, 0, now(), now())",
        )

        // travel_time_observation: one surviving (real pattern), one orphan (no matching trip_patterns row)
        jdbc.update(
            "insert into travel_time_observation (trip_pattern_id, stop_path_index, sample_count, mean_sec, updated_at) " +
                "values (100, 0, 1, 30.0, ?)",
            Timestamp.from(now),
        )
        jdbc.update(
            "insert into travel_time_observation (trip_pattern_id, stop_path_index, sample_count, mean_sec, updated_at) " +
                "values (999999, 0, 1, 30.0, ?)",
            Timestamp.from(now),
        )

        // kalman_travel_time_state: one surviving, one orphan
        jdbc.update(
            "insert into kalman_travel_time_state " +
                "(trip_pattern_id, stop_path_index, estimate_sec, error_variance, sample_count, updated_at) " +
                "values (100, 0, 30.0, 5.0, 1, ?)",
            Timestamp.from(now),
        )
        jdbc.update(
            "insert into kalman_travel_time_state " +
                "(trip_pattern_id, stop_path_index, estimate_sec, error_variance, sample_count, updated_at) " +
                "values (999999, 0, 30.0, 5.0, 1, ?)",
            Timestamp.from(now),
        )

        // vehicle_prediction: one old (deleted), one young (kept)
        jdbc.update(
            "insert into vehicle_prediction (feed_id, vehicle_id, trip_row_id, trip_pattern_id, stop_path_index, algorithm, computed_at) " +
                "values (1, 'v1', 1, 100, 0, 0, ?)",
            Timestamp.from(now.minus(10, ChronoUnit.HOURS)),
        )
        jdbc.update(
            "insert into vehicle_prediction (feed_id, vehicle_id, trip_row_id, trip_pattern_id, stop_path_index, algorithm, computed_at) " +
                "values (1, 'v2', 2, 100, 0, 0, ?)",
            Timestamp.from(now.minus(1, ChronoUnit.HOURS)),
        )

        // prediction_accuracy: one old (deleted), one young (kept)
        val oldAccuracyTs = Timestamp.from(now.minus(40, ChronoUnit.DAYS))
        jdbc.update(
            "insert into prediction_accuracy " +
                "(feed_id, vehicle_id, trip_row_id, stop_path_index, algorithm, " +
                "predicted_ts, actual_ts, error_sec, abs_error_sec, created_at) " +
                "values (1, 'v1', 1, 0, 0, ?, ?, 10, 10, ?)",
            oldAccuracyTs, oldAccuracyTs, oldAccuracyTs,
        )
        val youngAccuracyTs = Timestamp.from(now.minus(1, ChronoUnit.DAYS))
        jdbc.update(
            "insert into prediction_accuracy " +
                "(feed_id, vehicle_id, trip_row_id, stop_path_index, algorithm, " +
                "predicted_ts, actual_ts, error_sec, abs_error_sec, created_at) " +
                "values (1, 'v2', 2, 0, 0, ?, ?, 10, 10, ?)",
            youngAccuracyTs, youngAccuracyTs, youngAccuracyTs,
        )

        jdbc.update(
            "insert into avl_stop_crossing " +
                "(feed_id, vehicle_id, trip_row_id, trip_pattern_id, stop_path_index, observed_at, " +
                "observed_travel_time_sec, revision_id) " +
                "values (1, 'v1', 1, 100, 0, ?, 30.0, 1)",
            Timestamp.from(now.minus(100, ChronoUnit.DAYS)),
        )
        jdbc.update(
            "insert into avl_stop_crossing " +
                "(feed_id, vehicle_id, trip_row_id, trip_pattern_id, stop_path_index, observed_at, " +
                "observed_travel_time_sec, revision_id) " +
                "values (1, 'v2', 2, 100, 0, ?, 30.0, 1)",
            Timestamp.from(now.minus(1, ChronoUnit.DAYS)),
        )
    }

    @Test
    fun `prunes aged rows and sweeps orphan learner rows, keeping real-pattern and young rows`() {
        val counts = scheduler.prune(now)

        assertThat(counts).isEqualTo(
            PredictionRetentionScheduler.PruneCounts(
                predictions = 1, accuracy = 1, crossings = 1, orphanObservations = 1, orphanKalman = 1,
            ),
        )

        assertThat(
            jdbc.queryForList("select trip_pattern_id from travel_time_observation", Long::class.java),
        ).containsExactly(100L)
        assertThat(
            jdbc.queryForList("select trip_pattern_id from kalman_travel_time_state", Long::class.java),
        ).containsExactly(100L)
        assertThat(
            jdbc.queryForList("select vehicle_id from vehicle_prediction", String::class.java),
        ).containsExactly("v2")
        assertThat(
            jdbc.queryForList("select vehicle_id from prediction_accuracy", String::class.java),
        ).containsExactly("v2")
        assertThat(
            jdbc.queryForList("select vehicle_id from avl_stop_crossing", String::class.java),
        ).containsExactly("v2")
    }
}
