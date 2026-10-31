package eu.transittrack.db.bridge

import java.time.Instant
import javax.sql.DataSource

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

import eu.transittrack.AvlAssignmentMode
import eu.transittrack.AvlFormat
import eu.transittrack.avl.model.AvlFeed
import eu.transittrack.avl.model.AvlFeedRepository
import eu.transittrack.avl.model.AvlFeedSourceKind
import eu.transittrack.support.PostgresPerMethodTest

@SpringBootTest
@Import(BridgeProbe::class, BridgeProbeRepository::class)
class ExposedBridgeTest(
    @Autowired val probe: BridgeProbe,
    @Autowired val txManager: PlatformTransactionManager,
    @Autowired val avlFeedRepository: AvlFeedRepository,
    @Autowired val dataSource: DataSource,
) : PostgresPerMethodTest() {
    /** The test's own view of the database, outside any Spring transaction and outside the bridge. */
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

    private fun committedIds(): List<Long> = transaction(db) { BridgeProbeTable.selectAll().map { it[BridgeProbeTable.id] } }

    @Test
    fun exposedWriteRollsBackWithOuterJpaTransaction() {
        TransactionTemplate(txManager).executeWithoutResult { status ->
            probe.insert(1)
            status.setRollbackOnly()
        }

        assertThat(committedIds()).isEmpty()
    }

    @Test
    fun exposedSeesUnflushedJpaWriteInSameTransaction() {
        val count =
            TransactionTemplate(txManager).execute {
                avlFeedRepository.save(feed())
                probe.countAvlFeeds()
            }

        assertThat(count).isEqualTo(1L)
    }

    @Test
    fun exposedCommitsOnItsOwnOutsideTransaction() {
        probe.insert(1)

        assertThat(committedIds()).containsExactly(1L)
    }

    @Test
    fun requiresNewCommitsIndependently() {
        TransactionTemplate(txManager).executeWithoutResult { status ->
            probe.insertInNewTransaction(1)
            status.setRollbackOnly()
        }

        assertThat(committedIds()).containsExactly(1L)
    }

    @Test
    fun requiresNewFromInsideABridgedCallGetsItsOwnConnection() {
        TransactionTemplate(txManager).executeWithoutResult { status ->
            probe.insertAroundNewTransaction(id = 1, newId = 10, via = probe)
            assertThat(probe.ids().sorted()).containsExactly(1L, 2L, 10L)
            status.setRollbackOnly()
        }

        assertThat(committedIds()).containsExactly(10L)
    }

    private fun feed() =
        AvlFeed(
            code = "avl-bridge",
            name = "AVL bridge",
            gtfsFeedCode = "bridge",
            url = "http://x/avl",
            format = AvlFormat.GTFS_RT,
            pollIntervalSec = 30,
            assignmentMode = AvlAssignmentMode.TRUST_DESCRIPTOR,
            enabled = false,
            headers = null,
            source = AvlFeedSourceKind.CONFIG,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        )
}
