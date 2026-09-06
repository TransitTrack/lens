package eu.transittrack.gtfs.draft

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

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

    val NAMES: List<String> = GtfsTables.ALL.filterNot { it in DERIVED }

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
class DraftRowCopier(
    private val jdbc: JdbcTemplate,
) {
    /** For each raw table, INSERT ... SELECT every row of [fromRevisionId] as a new row of [toRevisionId]. */
    fun copyRawTables(
        fromRevisionId: Long,
        toRevisionId: Long,
    ): Map<String, Long> {
        val counts = LinkedHashMap<String, Long>()
        for (table in DraftRawTables.NAMES) {
            val cols = dataColumns(table)
            if (cols.isEmpty()) continue
            val colList = cols.joinToString(", ")
            val selectList =
                if (table == "trips") {
                    cols.joinToString(", ") { if (it in DraftRawTables.DERIVATION_OWNED_TRIP_COLUMNS) "NULL" else it }
                } else {
                    colList
                }
            val sql =
                """
                INSERT INTO $table ($colList, id, revision_id)
                SELECT $selectList, nextval('gtfs_entity_seq'), ?
                FROM $table WHERE revision_id = ?
                """.trimIndent()
            val n = jdbc.update(sql, toRevisionId, fromRevisionId)
            if (n > 0) counts[table] = n.toLong()
        }
        return counts
    }

    private fun dataColumns(table: String): List<String> =
        jdbc.query(
            """
            SELECT column_name FROM information_schema.columns
            WHERE table_schema = current_schema()
              AND table_name = ?
              AND column_name NOT IN ('id', 'revision_id')
            ORDER BY ordinal_position
            """.trimIndent(),
            { rs, _ -> rs.getString(1) },
            table,
        )
}
