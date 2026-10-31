package eu.transittrack.gtfs.draft

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.IColumnType
import org.jetbrains.exposed.v1.core.LiteralOp
import org.jetbrains.exposed.v1.core.Sequence
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.longParam
import org.jetbrains.exposed.v1.core.nextLongVal
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.springframework.stereotype.Component

import eu.transittrack.db.bridge.ExposedBridge
import eu.transittrack.gtfs.store.GtfsTables

/** Raw GTFS tables copied when forking a draft — everything revision-scoped except the derived schedule model. */
object DraftRawTables {
    private val DERIVED =
        setOf(
            "schedule_time",
            "block_trip",
            "travel_times_for_stop_path",
            "stop_path",
            "block",
            "trip_patterns",
        )

    val NAMES: List<String> = GtfsTables.ALL.map { it.tableName }.filterNot { it in DERIVED }

    /**
     * Columns on `trips` owned by schedule derivation (written by `DerivedGtfsWriter`, cleared by
     * `clearTripDerivation`). `trip_pattern_id` is a bare surrogate key into the *base* revision's
     * `trip_patterns`, so copying these verbatim would leave draft trips pointing across revisions.
     * The fork emits NULL for them; a later rebuild re-derives them.
     */
    val DERIVATION_OWNED_TRIP_COLUMNS =
        setOf("trip_pattern_id", "start_time_sec", "end_time_sec", "frequency_based", "no_schedule")
}

@Component
@ExposedBridge
class DraftRowCopier {
    /** For each raw table, INSERT ... SELECT every row of [fromRevisionId] as a new row of [toRevisionId]. */
    fun copyRawTables(
        fromRevisionId: Long,
        toRevisionId: Long,
    ): Map<String, Long> {
        val counts = LinkedHashMap<String, Long>()
        for (table in GtfsTables.ALL.filter { it.tableName in DraftRawTables.NAMES }) {
            val cols = table.columns.filter { it != table.id && it != table.revisionId }
            if (cols.isEmpty()) continue
            val selected: List<Expression<*>> =
                cols.map { col ->
                    if (table.tableName == "trips" && col.name in DraftRawTables.DERIVATION_OWNED_TRIP_COLUMNS) nullLiteral(col) else col
                } +
                    listOf(NEXT_ID.nextLongVal(), longParam(toRevisionId))
            val n =
                table.insert(
                    table.select(selected).where { table.revisionId eq fromRevisionId },
                    columns = cols + listOf(table.id, table.revisionId),
                )
            if (n != null && n > 0) counts[table.tableName] = n.toLong()
        }
        return counts
    }

    @Suppress("UNCHECKED_CAST")
    private fun nullLiteral(col: Column<*>): Expression<*> = LiteralOp(col.columnType as IColumnType<Any>, null)

    private companion object {
        val NEXT_ID = Sequence("gtfs_entity_seq")
    }
}
