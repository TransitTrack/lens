package eu.transittrack.gtfs.read

import eu.transittrack.gtfs.store.GtfsTables
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service

/**
 * Generic long-tail read: dumps every row of one revision-scoped `gtfs_*` table as a
 * raw `Map` (serialized by the GraphQL `JSON` scalar).
 *
 * The table is chosen from the schema `GtfsTable` enum, whose values are the GTFS
 * *file* names (plural — `FARE_PRODUCTS`), while the physical tables are singular
 * (`gtfs_fare_product`); [TABLE_BY_ENUM] bridges the two. The resulting name is a
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
        /** Schema `GtfsTable` enum value -> physical `gtfs_*` table name. */
        val TABLE_BY_ENUM: Map<String, String> = mapOf(
            "FARE_ATTRIBUTES" to "gtfs_fare_attribute",
            "FARE_RULES" to "gtfs_fare_rule",
            "TIMEFRAMES" to "gtfs_timeframe",
            "RIDER_CATEGORIES" to "gtfs_rider_category",
            "FARE_MEDIA" to "gtfs_fare_media",
            "FARE_PRODUCTS" to "gtfs_fare_product",
            "FARE_LEG_RULES" to "gtfs_fare_leg_rule",
            "FARE_LEG_JOIN_RULES" to "gtfs_fare_leg_join_rule",
            "FARE_TRANSFER_RULES" to "gtfs_fare_transfer_rule",
            "AREAS" to "gtfs_area",
            "STOP_AREAS" to "gtfs_stop_area",
            "NETWORKS" to "gtfs_network",
            "ROUTE_NETWORKS" to "gtfs_route_network",
            "LOCATION_GROUPS" to "gtfs_location_group",
            "LOCATION_GROUP_STOPS" to "gtfs_location_group_stop",
            "LOCATIONS" to "gtfs_location",
            "BOOKING_RULES" to "gtfs_booking_rule",
            "TRANSLATIONS" to "gtfs_translation",
            "ATTRIBUTIONS" to "gtfs_attribution",
        )
    }
}
