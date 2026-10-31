package eu.transittrack.db.bridge

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.springframework.boot.test.context.TestComponent
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/** Scratch table, created and dropped around each bridge test; not part of the Liquibase schema. */
object BridgeProbeTable : Table("bridge_probe") {
    val id = long("id")
    override val primaryKey = PrimaryKey(id)
}

/** Just enough of `avl_feed` to count its rows from Exposed. */
object AvlFeedIdTable : Table("avl_feed") {
    val id = long("id")
}

/**
 * Runs Exposed statements through the bridge. A [TestComponent] (and `@Import`ed by the tests
 * that need it) so component scanning doesn't add it to every other test's context.
 */
@TestComponent
@ExposedBridge
class BridgeProbe {
    fun insert(id: Long) {
        BridgeProbeTable.insert { it[BridgeProbeTable.id] = id }
    }

    fun ids(): List<Long> = BridgeProbeTable.selectAll().map { it[BridgeProbeTable.id] }

    fun countAvlFeeds(): Long = AvlFeedIdTable.selectAll().count()

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun insertInNewTransaction(id: Long) = insert(id)

    /**
     * Inserts [id], then [newId] through [via]'s `REQUIRES_NEW` method while this method's Exposed
     * transaction is still open, then [id] + 1 — so the outer Exposed transaction must survive
     * the inner one.
     */
    fun insertAroundNewTransaction(
        id: Long,
        newId: Long,
        via: BridgeProbe,
    ) {
        insert(id)
        via.insertInNewTransaction(newId)
        insert(id + 1)
    }
}

/** Same scratch-table insert, but on a `@Repository`, so persistence exception translation applies. */
@TestComponent
@Repository
@ExposedBridge
class BridgeProbeRepository {
    fun insert(id: Long) {
        BridgeProbeTable.insert { it[BridgeProbeTable.id] = id }
    }
}
