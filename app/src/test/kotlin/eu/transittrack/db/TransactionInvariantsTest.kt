package eu.transittrack.db

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.prop
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.transaction.support.TransactionTemplate

import eu.transittrack.gtfs.model.FeedInfoRepository
import eu.transittrack.support.PostgresPerMethodTest

/**
 * The transaction guarantees the application relies on, checked against the real transaction
 * manager: `@Transactional` boundaries commit and roll back Exposed writes, `REQUIRES_NEW` commits
 * on its own, and SQL failures — on a statement or at commit — leave as Spring's
 * `DataAccessException`s.
 *
 * Same `@Import` set as `ExposedExceptionTranslatorTest`, so both share one cached Spring context.
 */
@SpringBootTest
@Import(TxProbe::class, TxProbeRepository::class)
class TransactionInvariantsTest(
    @Autowired val probe: TxProbe,
    @Autowired val repository: TxProbeRepository,
    @Autowired val txManager: PlatformTransactionManager,
    @Autowired val db: Database,
    @Autowired val feedInfos: FeedInfoRepository,
) : PostgresPerMethodTest() {
    @BeforeEach
    fun createScratchTables() {
        transaction(db) {
            SchemaUtils.create(ProbeTable)
            exec(DeferredProbeTable.DDL)
        }
    }

    @AfterEach
    fun dropScratchTables() {
        transaction(db) { SchemaUtils.drop(ProbeTable, DeferredProbeTable) }
    }

    /** What is committed, read in a transaction of its own. */
    private fun committedIds(): List<Long> = transaction(db) { ProbeTable.selectAll().map { it[ProbeTable.id] }.sorted() }

    @Test
    fun exceptionInTransactionalMethodRollsBackItsWrites() {
        assertFailure { probe.insertThenFail(1) }
            .isInstanceOf<ProbeFailure>()
            .prop(ProbeFailure::seen)
            .containsExactly(1L)

        assertThat(committedIds()).isEmpty()
    }

    @Test
    fun requiresNewCommitsWhenTheOuterTransactionRollsBack() {
        assertFailure { probe.insertAroundNewTransactionThenFail(id = 1, newId = 10, via = probe) }
            .isInstanceOf<ProbeFailure>()
            .prop(ProbeFailure::seen)
            .containsExactly(1L, 2L, 10L)

        assertThat(committedIds()).containsExactly(10L)
    }

    @Test
    fun requiresNewCommitsInsideAnOuterProgrammaticTransaction() {
        var seenInside = emptyList<Long>()
        TransactionTemplate(txManager).executeWithoutResult { status ->
            probe.insert(1)
            probe.insertInNewTransaction(10)
            seenInside = probe.ids()
            status.setRollbackOnly()
        }

        assertThat(seenInside).containsExactly(1L, 10L)
        assertThat(committedIds()).containsExactly(10L)
    }

    @Test
    fun callOutsideAnyTransactionCommitsOnReturn() {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse()

        probe.insert(1)

        assertThat(committedIds()).containsExactly(1L)
    }

    @Test
    fun inheritedRepositoryMethodRunsInATransaction() {
        // FeedInfoRepository declares nothing itself: findByRevisionId is RevisionScopedRepository's.
        assertThat(feedInfos.findByRevisionId(-1)).isEmpty()
    }

    @Test
    fun failedStatementRollsBackTheTransactionsEarlierWrites() {
        repository.insert(1)

        assertFailure { probe.insertThenDuplicate(id = 2, duplicate = 1) }.isInstanceOf<DataIntegrityViolationException>()

        assertThat(committedIds()).containsExactly(1L)
    }

    @Test
    fun constraintViolationSurfacesAsDataIntegrityViolation() {
        repository.insert(1)
        assertThat(committedIds()).containsExactly(1L)

        assertFailure { repository.insert(1) }.isInstanceOf<DataIntegrityViolationException>()
    }

    @Test
    fun constraintViolationAtCommitSurfacesAsDataIntegrityViolation() {
        assertFailure { repository.insertDeferredTwice(1) }.isInstanceOf<DataIntegrityViolationException>()

        assertThat(repository.deferredSeenBeforeCommit, name = "rows inserted before commit").isEqualTo(2L)
        assertThat(transaction(db) { DeferredProbeTable.selectAll().count() }, name = "rows committed").isEqualTo(0L)
    }
}
