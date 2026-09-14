package eu.transittrack.predict

import kotlin.test.Test

import assertk.assertThat
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
    fun `avl_feed has new prediction columns`() {
        val n = jdbc.queryForObject(
            "select count(*) from information_schema.columns where table_name = 'avl_feed' " +
                "and column_name in ('prediction_algorithm', 'prediction_mode')",
            Int::class.java,
        )
        assertThat(n).isEqualTo(2)
    }
}
