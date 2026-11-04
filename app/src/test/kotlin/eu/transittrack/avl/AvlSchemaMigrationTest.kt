package eu.transittrack.avl

import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.jetbrains.exposed.v1.jdbc.Database
import org.springframework.beans.factory.annotation.Autowired

import eu.transittrack.gtfs.support.PostgresSliceTest
import eu.transittrack.support.PostgresPerMethodTest
import eu.transittrack.support.catalogLong

@PostgresSliceTest
class AvlSchemaMigrationTest(
    @Autowired val db: Database,
) : PostgresPerMethodTest() {
    @Test
    fun `avl tables exist`() {
        for (t in listOf("avl_feed", "avl_report", "vehicle_match", "vehicle_state")) {
            val n = db.catalogLong("select count(*) from information_schema.tables where table_name = ?", t)
            assertThat(n).isEqualTo(1L)
        }
    }
}
