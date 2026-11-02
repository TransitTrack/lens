package eu.transittrack.db

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.springframework.boot.test.context.TestComponent
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/** Scratch table, created and dropped around each transaction test; not part of the Liquibase schema. */
object ProbeTable : Table("tx_probe") {
    val id = long("id")
    override val primaryKey = PrimaryKey(id)
}

/**
 * Scratch table whose unique constraint is `deferrable initially deferred` (created by the tests
 * with raw DDL, which Exposed cannot model), so a duplicate only fails at commit.
 */
object DeferredProbeTable : Table("tx_probe_deferred") {
    val id = long("id")

    const val DDL =
        "create table tx_probe_deferred (id bigint not null, " +
            "constraint tx_probe_deferred_id_key unique (id) deferrable initially deferred)"
}

/** Thrown by the probes once their writes are in; carries what the transaction saw before it failed. */
class ProbeFailure(
    val seen: List<Long>,
) : RuntimeException("probe failure after writing $seen")

/**
 * Runs Exposed statements under Spring's `@Transactional`, like the application's repositories. A
 * [TestComponent] (and `@Import`ed by the tests that need it) so component scanning doesn't add it
 * to every other test's context.
 */
@TestComponent
@Transactional
class TxProbe {
    fun insert(id: Long) {
        ProbeTable.insert { it[ProbeTable.id] = id }
    }

    fun ids(): List<Long> = ProbeTable.selectAll().map { it[ProbeTable.id] }.sorted()

    /** Inserts [id], then fails — after checking the row is visible inside this transaction. */
    fun insertThenFail(id: Long): Nothing {
        insert(id)
        throw ProbeFailure(ids())
    }

    /** Inserts [id], then [duplicate] — which must already exist, so that statement fails. */
    fun insertThenDuplicate(
        id: Long,
        duplicate: Long,
    ) {
        insert(id)
        insert(duplicate)
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun insertInNewTransaction(id: Long) = insert(id)

    /**
     * Inserts [id], then [newId] through [via]'s `REQUIRES_NEW` method while this method's
     * transaction is still open, then [id] + 1 — so this transaction must survive the inner one —
     * and fails, so it rolls back.
     */
    fun insertAroundNewTransactionThenFail(
        id: Long,
        newId: Long,
        via: TxProbe,
    ): Nothing {
        insert(id)
        via.insertInNewTransaction(newId)
        insert(id + 1)
        throw ProbeFailure(ids())
    }
}

/** Same scratch-table inserts, but on a `@Repository`. */
@TestComponent
@Repository
@Transactional
class TxProbeRepository {
    /** What [insertDeferredTwice] saw just before its transaction tried to commit. */
    var deferredSeenBeforeCommit: Long = -1

    fun insert(id: Long) {
        ProbeTable.insert { it[ProbeTable.id] = id }
    }

    /** Both statements succeed; the deferred unique constraint fails at commit. */
    fun insertDeferredTwice(id: Long) {
        repeat(2) { DeferredProbeTable.insert { it[DeferredProbeTable.id] = id } }
        deferredSeenBeforeCommit = DeferredProbeTable.selectAll().count()
    }
}
