package eu.transittrack.gtfs.store

import eu.transittrack.gtfs.model.RevisionScoped

/**
 * Bulk write path for revision-scoped GTFS rows.
 *
 * Implementations write via a JDBC-batched insert path (no persistence context) and
 * can wipe every row belonging to a single revision — used to clean up a FAILED
 * revision or to re-ingest.
 */
interface RevisionWriter {
    /** Insert every row. All rows should belong to the same (already-persisted) revision. */
    fun write(rows: List<RevisionScoped>)

    /** Delete every GTFS row (all tables) with the given `revision_id`. */
    fun deleteAllForRevision(revisionId: Long)
}

/**
 * Every `gtfs_*` entity table that carries a `revision_id` column.
 *
 * Ordered children/leaf tables first so the `DELETE`s never trip a FK. All FKs
 * currently point only at `gtfs_revision`, but this order is also what validation
 * and pruning expect.
 *
 * Deliberately excludes `gtfs_revision` and `gtfs_feed` (not revision-scoped rows).
 * Cross-checked against `git grep 'name = "gtfs_'` in `eu.transittrack.gtfs.model`
 * (33 tables).
 */
object GtfsTables {
    val ALL: List<String> = listOf(
        "gtfs_stop_time", "gtfs_shape_point", "gtfs_shape", "gtfs_frequency", "gtfs_transfer",
        "gtfs_trip", "gtfs_route_network", "gtfs_route",
        "gtfs_stop_area", "gtfs_location_group_stop", "gtfs_location_group", "gtfs_location",
        "gtfs_pathway", "gtfs_level", "gtfs_booking_rule",
        "gtfs_fare_leg_join_rule", "gtfs_fare_transfer_rule", "gtfs_fare_leg_rule",
        "gtfs_fare_rule", "gtfs_fare_attribute", "gtfs_fare_product", "gtfs_fare_media",
        "gtfs_rider_category", "gtfs_timeframe", "gtfs_area", "gtfs_network",
        "gtfs_calendar_date", "gtfs_calendar", "gtfs_stop", "gtfs_agency",
        "gtfs_feed_info", "gtfs_translation", "gtfs_attribution",
    )
}
