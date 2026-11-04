package eu.transittrack.gtfs.store

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

import eu.transittrack.gtfs.model.RevisionScoped
import eu.transittrack.gtfs.model.RevisionScopedTable

/**
 * Default [RevisionWriter]: routes each row by its runtime class to its table in [GtfsTables.ALL]
 * and JDBC-batch-inserts every group. A row class with no registered table is a programming error
 * and fails the whole write before anything is inserted.
 */
@Component
@Transactional
class ExposedRevisionWriter : RevisionWriter {
    private val registry: Map<Class<out RevisionScoped>, RevisionScopedTable<*>> = GtfsTables.ALL.associateBy { it.rowClass }

    /** Joins the caller's transaction; its own when there is none. */
    @Transactional
    override fun write(rows: List<RevisionScoped>) {
        if (rows.isEmpty()) return
        val groups =
            rows.groupBy { it.javaClass }.map { (type, items) ->
                (registry[type] ?: throw IllegalStateException("No table registered for ${type.name}")) to items
            }
        for ((table, items) in groups) insert(table, items)
    }

    private fun <R : RevisionScoped> insert(
        table: RevisionScopedTable<R>,
        items: List<RevisionScoped>,
    ) {
        @Suppress("UNCHECKED_CAST")
        table.batchInsert(items as List<R>, shouldReturnGeneratedValues = false) { table.bind(this, it) }
    }

    /**
     * Commits on its own, independent of any caller transaction: callers such as
     * `DraftService.revertToFork` rely on the wipe standing even when
     * they roll back.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    override fun deleteAllForRevision(revisionId: Long) {
        for (table in GtfsTables.ALL) {
            table.deleteWhere { table.revisionId eq revisionId }
        }
    }
}
