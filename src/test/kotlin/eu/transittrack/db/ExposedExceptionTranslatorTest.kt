package eu.transittrack.db

import javax.sql.DataSource

import assertk.assertFailure
import assertk.assertions.isInstanceOf
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException

import eu.transittrack.db.bridge.BridgeProbe
import eu.transittrack.db.bridge.BridgeProbeRepository
import eu.transittrack.db.bridge.BridgeProbeTable
import eu.transittrack.support.PostgresPerMethodTest

// Same @Import set as ExposedBridgeTest, so both share one cached Spring context.
@SpringBootTest
@Import(BridgeProbe::class, BridgeProbeRepository::class)
class ExposedExceptionTranslatorTest(
    @Autowired val repository: BridgeProbeRepository,
    @Autowired val dataSource: DataSource,
) : PostgresPerMethodTest() {
    private lateinit var db: Database

    @BeforeEach
    fun createScratchTable() {
        db = Database.connect(dataSource)
        transaction(db) { SchemaUtils.create(BridgeProbeTable) }
    }

    @AfterEach
    fun dropScratchTable() {
        transaction(db) { SchemaUtils.drop(BridgeProbeTable) }
        TransactionManager.closeAndUnregister(db)
    }

    @Test
    fun uniqueViolationBecomesDataIntegrityViolation() {
        repository.insert(1)

        assertFailure { repository.insert(1) }.isInstanceOf<DataIntegrityViolationException>()
    }
}
