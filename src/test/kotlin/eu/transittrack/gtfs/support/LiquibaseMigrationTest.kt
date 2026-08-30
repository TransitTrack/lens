package eu.transittrack.gtfs.support

import jakarta.persistence.EntityManager
import kotlin.test.Test
import kotlin.test.assertEquals
import org.springframework.beans.factory.annotation.Autowired

@PostgresSliceTest
class LiquibaseMigrationTest(@Autowired val em: EntityManager) {

    @Test
    fun `gtfs_entity_seq exists after migration`() {
        val count = em.createNativeQuery(
            "select count(*) from information_schema.sequences where sequence_name = 'gtfs_entity_seq'"
        ).singleResult as Number
        assertEquals(1, count.toInt())
    }
}
