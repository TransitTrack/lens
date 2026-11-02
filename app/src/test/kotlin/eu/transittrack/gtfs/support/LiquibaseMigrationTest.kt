package eu.transittrack.gtfs.support

import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.beans.factory.annotation.Autowired

import eu.transittrack.support.PostgresPerMethodTest

@PostgresSliceTest
class LiquibaseMigrationTest(
    @Autowired val database: Database,
) : PostgresPerMethodTest() {
    @Test
    fun `gtfs_entity_seq exists after migration`() {
        val count =
            transaction(database) {
                exec("select count(*) from information_schema.sequences where sequence_name = 'gtfs_entity_seq'") { rs ->
                    rs.next()
                    rs.getInt(1)
                }
            }
        assertThat(count).isEqualTo(1)
    }
}
