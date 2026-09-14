package eu.transittrack.avl

import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate

import eu.transittrack.gtfs.support.PostgresSliceTest
import eu.transittrack.support.PostgresPerMethodTest

@PostgresSliceTest
class AvlSchemaMigrationTest(
    @Autowired val jdbc: JdbcTemplate,
) : PostgresPerMethodTest() {
    @Test
    fun `avl tables exist`() {
        for (t in listOf("avl_feed", "avl_report", "vehicle_match", "vehicle_state")) {
            val n = jdbc.queryForObject(
                "select count(*) from information_schema.tables where table_name = ?",
                Int::class.java,
                t,
            )
            assertThat(n).isEqualTo(1)
        }
    }
}
