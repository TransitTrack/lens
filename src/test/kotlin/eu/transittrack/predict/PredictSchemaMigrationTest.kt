package eu.transittrack.predict

import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate

import eu.transittrack.gtfs.support.PostgresSliceTest
import eu.transittrack.support.PostgresPerMethodTest

@PostgresSliceTest
class PredictSchemaMigrationTest(
    @Autowired val jdbc: JdbcTemplate,
) : PostgresPerMethodTest() {
    @Test
    fun `predict tables exist`() {
        for (t in listOf(
            "travel_time_observation",
            "kalman_travel_time_state",
            "vehicle_prediction",
            "prediction_accuracy",
            "prediction_checkpoint",
            "prediction_frequency_run",
        )) {
            val n = jdbc.queryForObject(
                "select count(*) from information_schema.tables where table_name = ?",
                Int::class.java,
                t,
            )
            assertThat(n).isEqualTo(1)
        }
    }

    @Test
    fun `avl_feed has new prediction column`() {
        val n = jdbc.queryForObject(
            "select count(*) from information_schema.columns where table_name = 'avl_feed' " +
                "and column_name = 'prediction_algorithms'",
            Int::class.java,
        )
        assertThat(n).isEqualTo(1)
    }

    @Test
    fun `prediction claim query is served by a partial index on pending matches`() {
        val def = jdbc.queryForObject(
            "select indexdef from pg_indexes where tablename = 'vehicle_match' and indexname = 'ix_vehicle_match_pending'",
            String::class.java,
        )!!
        assertThat(def).contains("(feed_code, ts, vehicle_id)")
        assertThat(def).contains("WHERE (prediction_status = 0)")
    }

    @Test
    fun `prediction_accuracy records the horizon and time each prediction was made`() {
        val n = jdbc.queryForObject(
            "select count(*) from information_schema.columns where table_name = 'prediction_accuracy' " +
                "and column_name in ('horizon_sec', 'predicted_at', 'actual_lead_sec', 'produced_at')",
            Int::class.java,
        )
        assertThat(n).isEqualTo(4)
    }
}
