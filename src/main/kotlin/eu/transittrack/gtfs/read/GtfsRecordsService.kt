package eu.transittrack.gtfs.read

import eu.transittrack.gtfs.store.GtfsTables
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service

/**
 * Generic long-tail read: dumps every row of one revision-scoped `gtfs_*` table as a
 * raw `Map` (serialized by the GraphQL `JSON` scalar).
 *
 * The table is chosen from the schema `GtfsTable` enum, whose values are the GTFS
 * *file* names (`FARE_PRODUCTS`); the physical tables are the pluralised, un-prefixed
 * form (`fare_products`); [TABLE_BY_ENUM] bridges the two. The resulting name is a
 * validated member of [GtfsTables.ALL], so the `select *` string interpolation is
 * safe (no user text reaches the SQL).
 */
@Service
class GtfsRecordsService(
    private val resolver: RevisionResolver,
    private val jdbc: JdbcTemplate,
) {
    fun records(feedCode: String, tableEnum: String, revisionId: String?): List<Map<String, Any?>> {
        val table = TABLE_BY_ENUM[tableEnum]
            ?: throw IllegalArgumentException("unknown table '$tableEnum'")
        require(table in GtfsTables.ALL) { "unknown table '$tableEnum'" }
        val rev = resolver.resolve(feedCode, revisionId)
        return jdbc.queryForList("select * from $table where revision_id = ?", rev)
    }

    companion object {
        /** Schema `GtfsTable` enum value -> physical table name. */
        val TABLE_BY_ENUM: Map<String, String> = mapOf(
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
