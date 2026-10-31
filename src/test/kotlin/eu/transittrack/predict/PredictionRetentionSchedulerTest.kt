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
import eu.transittrack.util.observability.TransitTrackMetrics

@PostgresSliceTest
class PredictionRetentionSchedulerTest(
    @Autowired val jdbc: JdbcTemplate,
) : PostgresPerMethodTest() {
    private val now = Instant.parse("2026-09-05T12:00:00Z")
    private lateinit var scheduler: PredictionRetentionScheduler

    @BeforeEach
    fun seed() {
        scheduler = PredictionRetentionScheduler(jdbc, PredictProperties(), TransitTrackMetrics.forTests())

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
                "assignment_mode, enabled, source, prediction_algorithms, created_at, updated_at) " +
                "values (1, 'f1', 'F1', 'g', 'http://x', 0, 30, 0, true, 'CONFIG', '[\"SCHEDULE_ADHERENCE\"]', now(), now())",
        )

        // Learned rows are keyed by (gtfs feed code, pattern_key): one surviving (the pattern still
        // exists in a stored revision of feed g), one whose key no revision has any more, and one
        // with the right key but under another feed - both orphans.
        for ((feedCode, patternKey) in listOf("g" to "k", "g" to "gone", "other" to "k")) {
            jdbc.update(
                "insert into travel_time_observation " +
                    "(gtfs_feed_code, pattern_key, stop_path_index, day_type, time_band, sample_count, mean_sec, updated_at) " +
                    "values (?, ?, 0, 3, 5, 1, 30.0, ?)",
                feedCode, patternKey, Timestamp.from(now),
            )
            jdbc.update(
                "insert into kalman_travel_time_state " +
                    "(gtfs_feed_code, pattern_key, stop_path_index, day_type, time_band, " +
                    "estimate_sec, error_variance, sample_count, updated_at) " +
                    "values (?, ?, 0, 3, 5, 30.0, 5.0, 1, ?)",
                feedCode, patternKey, Timestamp.from(now),
            )
        }

        // vehicle_prediction: one old (deleted), one young (kept)
        jdbc.update(
            "insert into vehicle_prediction " +
                "(feed_code, vehicle_id, trip_row_id, trip_pattern_id, stop_path_index, algorithm, computed_at) " +
                "values ('f1', 'v1', 1, 100, 0, 0, ?)",
            Timestamp.from(now.minus(10, ChronoUnit.HOURS)),
        )
        jdbc.update(
            "insert into vehicle_prediction " +
                "(feed_code, vehicle_id, trip_row_id, trip_pattern_id, stop_path_index, algorithm, computed_at) " +
                "values ('f1', 'v2', 2, 100, 0, 0, ?)",
            Timestamp.from(now.minus(1, ChronoUnit.HOURS)),
        )

        // prediction_checkpoint: one never scored within a prediction's lifetime (deleted), one young (kept)
        for ((vehicleId, age) in listOf("v1" to 10L, "v2" to 1L)) {
            val ts = Timestamp.from(now.minus(age, ChronoUnit.HOURS))
            jdbc.update(
                "insert into prediction_checkpoint " +
                    "(feed_code, vehicle_id, trip_row_id, stop_path_index, algorithm, " +
                    "sample_slot, predicted_ts, computed_at, produced_at) " +
                    "values ('f1', ?, 1, 0, 0, ?, ?, ?, ?)",
                vehicleId, ts, ts, ts, ts,
            )
            jdbc.update(
                "insert into prediction_frequency_run (feed_code, vehicle_id, trip_row_id, service_date, run_started_at, last_report_ts) " +
                    "values ('f1', ?, 1, date '2026-09-05', ?, ?)",
                vehicleId, ts, ts,
            )
        }

        // prediction_accuracy: one old (deleted), one young (kept)
        val oldAccuracyTs = Timestamp.from(now.minus(40, ChronoUnit.DAYS))
        jdbc.update(
            "insert into prediction_accuracy " +
                "(feed_code, vehicle_id, trip_row_id, stop_path_index, algorithm, " +
                "predicted_ts, actual_ts, error_sec, abs_error_sec, created_at) " +
                "values ('f1', 'v1', 1, 0, 0, ?, ?, 10, 10, ?)",
            oldAccuracyTs, oldAccuracyTs, oldAccuracyTs,
        )
        val youngAccuracyTs = Timestamp.from(now.minus(1, ChronoUnit.DAYS))
        jdbc.update(
            "insert into prediction_accuracy " +
                "(feed_code, vehicle_id, trip_row_id, stop_path_index, algorithm, " +
                "predicted_ts, actual_ts, error_sec, abs_error_sec, created_at) " +
                "values ('f1', 'v2', 2, 0, 0, ?, ?, 10, 10, ?)",
            youngAccuracyTs, youngAccuracyTs, youngAccuracyTs,
        )

        jdbc.update(
            "insert into avl_stop_crossing " +
                "(feed_code, vehicle_id, trip_row_id, trip_pattern_id, stop_path_index, observed_at, " +
                "observed_travel_time_sec, revision_id) " +
                "values ('f1', 'v1', 1, 100, 0, ?, 30.0, 1)",
            Timestamp.from(now.minus(100, ChronoUnit.DAYS)),
        )
        jdbc.update(
            "insert into avl_stop_crossing " +
                "(feed_code, vehicle_id, trip_row_id, trip_pattern_id, stop_path_index, observed_at, " +
                "observed_travel_time_sec, revision_id) " +
                "values ('f1', 'v2', 2, 100, 0, ?, 30.0, 1)",
            Timestamp.from(now.minus(1, ChronoUnit.DAYS)),
        )
    }

    @Test
    fun `prunes aged rows and sweeps orphan learner rows, keeping real-pattern and young rows`() {
        val counts = scheduler.prune(now)

        assertThat(counts).isEqualTo(
            PredictionRetentionScheduler.PruneCounts(
                predictions = 1, checkpoints = 1, accuracy = 1, crossings = 1, orphanObservations = 2, orphanKalman = 2,
                frequencyRuns = 1,
            ),
        )

        assertThat(
            jdbc.queryForList("select gtfs_feed_code || '/' || pattern_key from travel_time_observation", String::class.java),
        ).containsExactly("g/k")
        assertThat(
            jdbc.queryForList("select gtfs_feed_code || '/' || pattern_key from kalman_travel_time_state", String::class.java),
        ).containsExactly("g/k")
        assertThat(
            jdbc.queryForList("select vehicle_id from vehicle_prediction", String::class.java),
        ).containsExactly("v2")
        assertThat(
            jdbc.queryForList("select vehicle_id from prediction_frequency_run", String::class.java),
        ).containsExactly("v2")
        assertThat(
            jdbc.queryForList("select vehicle_id from prediction_checkpoint", String::class.java),
        ).containsExactly("v2")
        assertThat(
            jdbc.queryForList("select vehicle_id from prediction_accuracy", String::class.java),
        ).containsExactly("v2")
        assertThat(
            jdbc.queryForList("select vehicle_id from avl_stop_crossing", String::class.java),
        ).containsExactly("v2")
    }
}
