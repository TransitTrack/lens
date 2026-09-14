package eu.transittrack.gtfs.support

import jakarta.persistence.EntityManager
import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.springframework.beans.factory.annotation.Autowired

import eu.transittrack.support.PostgresPerMethodTest

@PostgresSliceTest
class LiquibaseMigrationTest(
    @Autowired val em: EntityManager,
) : PostgresPerMethodTest() {
    @Test
    fun `gtfs_entity_seq exists after migration`() {
        val count =
            em
                .createNativeQuery(
                    "select count(*) from information_schema.sequences where sequence_name = 'gtfs_entity_seq'",
                ).singleResult as Number
        assertThat(count.toInt()).isEqualTo(1)
    }
}
