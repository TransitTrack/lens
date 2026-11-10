package eu.transittrack.gtfs.read

import java.sql.Date
import java.time.LocalDate

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.JsonColumnMarker
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.postgresql.util.PGobject
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

import eu.transittrack.gtfs.model.RevisionScopedTable
import eu.transittrack.gtfs.store.GtfsTables

/**
 * Generic long-tail read: dumps every row of one revision-scoped `gtfs_*` table as a raw `Map`
 * (serialized as plain JSON).
 *
 * The table is chosen from the schema `GtfsTable` enum, whose values are the GTFS *file* names
 * (`FARE_PRODUCTS`); the physical tables are the pluralised, un-prefixed form (`fare_products`);
 * [TABLE_BY_ENUM] bridges the two. The table is looked up among [GtfsTables.ALL], so no user text
 * reaches the SQL. Values keep the types the JDBC driver returned for raw rows (smallint as
 * `Int`, `date` as `java.sql.Date`, `jsonb` as `PGobject`), see [rawValue].
 */
@Service
@Transactional
class GtfsRecordsService(
    private val resolver: RevisionResolver,
) {
    fun records(
        feedCode: String,
        tableEnum: String,
        revisionId: String?,
    ): List<Map<String, Any?>> {
        val table = TABLE_BY_ENUM[tableEnum] ?: throw IllegalArgumentException("unknown table '$tableEnum'")
        val exposed = GtfsTables.ALL.firstOrNull { it.tableName == table } ?: throw IllegalArgumentException("unknown table '$tableEnum'")
        val rev = resolver.resolve(feedCode, revisionId)
        return exposed.selectAll().where { exposed.revisionId eq rev }.map { toMap(exposed, it) }
    }

    private fun toMap(
        table: RevisionScopedTable<*>,
        row: ResultRow,
    ): Map<String, Any?> {
        val out = LinkedHashMap<String, Any?>()
        for (col in table.columns) out[col.name] = rawValue(col, row[col])
        return out
    }

    // Exposed's typed columns narrow what the driver's getObject returned; undo that.
    private fun rawValue(
        col: Column<*>,
        value: Any?,
    ): Any? =
        when {
            value is Short -> {
                value.toInt()
            }

            value is LocalDate -> {
                Date.valueOf(value)
            }

            value is String && col.columnType is JsonColumnMarker -> {
                PGobject().apply {
                    type = if ((col.columnType as JsonColumnMarker).usesBinaryFormat) "jsonb" else "json"
                    this.value = value
                }
            }

            else -> {
                value
            }
        }

    companion object {
        /** Schema `GtfsTable` enum value -> physical table name. */
        val TABLE_BY_ENUM: Map<String, String> =
            mapOf(
                "FARE_ATTRIBUTES" to "fare_attributes",
                "FARE_RULES" to "fare_rules",
                "TIMEFRAMES" to "timeframes",
                "RIDER_CATEGORIES" to "rider_categories",
                "FARE_MEDIA" to "fare_media",
                "FARE_PRODUCTS" to "fare_products",
                "FARE_LEG_RULES" to "fare_leg_rules",
                "FARE_LEG_JOIN_RULES" to "fare_leg_join_rules",
                "FARE_TRANSFER_RULES" to "fare_transfer_rules",
                "AREAS" to "areas",
                "STOP_AREAS" to "stop_areas",
                "NETWORKS" to "networks",
                "ROUTE_NETWORKS" to "route_networks",
                "LOCATION_GROUPS" to "location_groups",
                "LOCATION_GROUP_STOPS" to "location_group_stops",
                "LOCATIONS" to "locations",
                "BOOKING_RULES" to "booking_rules",
                "TRANSLATIONS" to "translations",
                "ATTRIBUTIONS" to "attributions",
            )
    }
}
