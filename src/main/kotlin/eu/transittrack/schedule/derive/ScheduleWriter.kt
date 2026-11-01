package eu.transittrack.schedule.derive

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.update
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

import eu.transittrack.db.bridge.ExposedBridge
import eu.transittrack.gtfs.model.RevisionScoped
import eu.transittrack.gtfs.model.RevisionScopedTable
import eu.transittrack.schedule.model.Block
import eu.transittrack.schedule.model.ScheduleTables
import eu.transittrack.schedule.model.StopPath
import eu.transittrack.schedule.model.StopPathTable
import eu.transittrack.schedule.model.TripPattern
import eu.transittrack.schedule.model.TripPatternTable

data class StopPathLayoverUpdate(
    val stopPathId: Long,
    val layoverStop: Boolean,
    val breakTimeSec: Int?,
)

/**
 * Bulk write path for the derived schedule model ([ScheduleTables]), JDBC-batched.
 *
 * Every public method commits in its own transaction (`REQUIRES_NEW`), as the Hibernate stateless
 * session it replaced did: derivation runs with no caller transaction, and `DraftService`'s
 * discard / revert-to-fork rely on [deleteForRevision] standing even when they roll back.
 */
@Component
@ConditionalOnProperty(name = ["transittrack.schedule.enabled"], havingValue = "true")
@ExposedBridge
class ScheduleWriter {
    private val registry: Map<Class<out RevisionScoped>, RevisionScopedTable<*>> = ScheduleTables.ALL.associateBy { it.rowClass }
    private val byName: Map<String, RevisionScopedTable<*>> = ScheduleTables.ALL.associateBy { it.tableName }

    /**
     * Inserts [rows], grouped by type in first-seen order. Rows whose ids derivation reads back
     * ([ID_READ_BACK]) get them set; the rest are inserted without returning keys.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun write(rows: List<RevisionScoped>) {
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
        val rows = items as List<R>
        val readBack = table.rowClass in ID_READ_BACK
        val inserted = table.batchInsert(rows, shouldReturnGeneratedValues = readBack) { table.bind(this, it) }
        if (!readBack) return
        check(inserted.size == rows.size) { "${table.tableName}: ${inserted.size} keys returned for ${rows.size} rows" }
        rows.zip(inserted).forEach { (row, result) -> row.id = result[table.id] }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun deleteForRevision(revisionId: Long) = deleteTables(revisionId, *DELETE_ORDER.toTypedArray())

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun deleteTables(
        revisionId: Long,
        vararg tables: String,
    ) {
        if (tables.isEmpty()) return
        val targets = tables.map { byName[it] ?: throw IllegalArgumentException("not a derived schedule table: $it") }
        for (table in targets) {
            table.deleteWhere { table.revisionId eq revisionId }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun applyStopPathLayover(updates: List<StopPathLayoverUpdate>) {
        if (updates.isEmpty()) return
        for (u in updates) {
            StopPathTable.update({ StopPathTable.id eq u.stopPathId }) {
                it[layoverStop] = u.layoverStop
                it[breakTimeSec] = u.breakTimeSec
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun resetStopPathLayover(revisionId: Long) {
        StopPathTable.update({ StopPathTable.revisionId eq revisionId }) {
            it[layoverStop] = StopPathTable.stopPathIndex eq 0
            it[breakTimeSec] = null
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun resetTripPatternTripCount(revisionId: Long) {
        TripPatternTable.update({ TripPatternTable.revisionId eq revisionId }) { it[tripCount] = 0 }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun applyTripPatternTripCount(counts: Map<Long, Int>) {
        if (counts.isEmpty()) return
        for ((id, tc) in counts) {
            TripPatternTable.update({ TripPatternTable.id eq id }) { it[tripCount] = tc }
        }
    }

    companion object {
        val DELETE_ORDER =
            listOf("schedule_time", "block_trip", "travel_times_for_stop_path", "stop_path", "block", "trip_patterns")

        /** Row types whose generated ids derivation reads after [write] (to key child rows and caches). */
        private val ID_READ_BACK: Set<Class<out RevisionScoped>> = setOf(TripPattern::class.java, StopPath::class.java, Block::class.java)
    }
}
