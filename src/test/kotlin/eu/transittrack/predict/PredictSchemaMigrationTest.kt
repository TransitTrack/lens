package eu.transittrack.predict

import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import org.jetbrains.exposed.v1.jdbc.Database
import org.springframework.beans.factory.annotation.Autowired

import eu.transittrack.gtfs.support.PostgresSliceTest
import eu.transittrack.support.PostgresPerMethodTest
import eu.transittrack.support.catalogLong
import eu.transittrack.support.catalogString

@PostgresSliceTest
class PredictSchemaMigrationTest(
    @Autowired val db: Database,
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
            val n = db.catalogLong("select count(*) from information_schema.tables where table_name = ?", t)
            assertThat(n).isEqualTo(1L)
        }
    }

    @Test
    fun `avl_feed has new prediction column`() {
        val n = db.catalogLong(
            "select count(*) from information_schema.columns where table_name = 'avl_feed' " +
                "and column_name = 'prediction_algorithms'",
        )
        assertThat(n).isEqualTo(1L)
    }

    @Test
    fun `prediction claim query is served by a partial index on pending matches`() {
        val def = db.catalogString(
            "select indexdef from pg_indexes where tablename = 'vehicle_match' and indexname = 'ix_vehicle_match_pending'",
        )
        assertThat(def).contains("(feed_code, ts, vehicle_id)")
        assertThat(def).contains("WHERE (prediction_status = 0)")
    }

    @Test
    fun `prediction_accuracy records the horizon and time each prediction was made`() {
        val n = db.catalogLong(
            "select count(*) from information_schema.columns where table_name = 'prediction_accuracy' " +
                "and column_name in ('horizon_sec', 'predicted_at', 'actual_lead_sec', 'produced_at')",
        )
        assertThat(n).isEqualTo(4L)
    }
}
