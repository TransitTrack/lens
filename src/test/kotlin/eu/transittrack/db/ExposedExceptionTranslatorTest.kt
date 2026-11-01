package eu.transittrack.db

import assertk.assertFailure
import assertk.assertions.isInstanceOf
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException

import eu.transittrack.support.PostgresPerMethodTest

// Same @Import set as TransactionInvariantsTest, so both share one cached Spring context.
@SpringBootTest
@Import(TxProbe::class, TxProbeRepository::class)
class ExposedExceptionTranslatorTest(
    @Autowired val repository: TxProbeRepository,
    @Autowired val db: Database,
) : PostgresPerMethodTest() {
    @BeforeEach
    fun createScratchTable() {
        transaction(db) { SchemaUtils.create(ProbeTable) }
    }

    @AfterEach
    fun dropScratchTable() {
        transaction(db) { SchemaUtils.drop(ProbeTable) }
    }

    @Test
    fun uniqueViolationBecomesDataIntegrityViolation() {
        repository.insert(1)

        assertFailure { repository.insert(1) }.isInstanceOf<DataIntegrityViolationException>()
    }
}
