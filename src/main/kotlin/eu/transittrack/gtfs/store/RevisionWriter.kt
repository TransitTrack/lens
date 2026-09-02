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
 * Every GTFS entity table that carries a `revision_id` column.
 *
 * Ordered children/leaf tables first so the `DELETE`s never trip a FK. All FKs
 * currently point only at `gtfs_revision`, but this order is also what validation
 * and pruning expect.
 *
 * Deliberately excludes `gtfs_revision` and `gtfs_feed` (not revision-scoped rows).
 * Cross-checked against `git grep '@Table(name' in `eu.transittrack.gtfs.model`
 * (33 tables).
 */
object GtfsTables {
    val ALL: List<String> = listOf(
        "stop_times", "shape_points", "shapes", "frequencies", "transfers",
        "trips", "route_networks", "routes",
        "stop_areas", "location_group_stops", "location_groups", "locations",
        "pathways", "levels", "booking_rules",
        "fare_leg_join_rules", "fare_transfer_rules", "fare_leg_rules",
        "fare_rules", "fare_attributes", "fare_products", "fare_media",
        "rider_categories", "timeframes", "areas", "networks",
        "calendar_dates", "calendars", "stops", "agencies",
        "feed_infos", "translations", "attributions",
    )
}
