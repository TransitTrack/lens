package eu.transittrack.gtfs.store

import eu.transittrack.gtfs.model.AgencyTable
import eu.transittrack.gtfs.model.AreaTable
import eu.transittrack.gtfs.model.AttributionTable
import eu.transittrack.gtfs.model.BookingRuleTable
import eu.transittrack.gtfs.model.CalendarDateTable
import eu.transittrack.gtfs.model.CalendarTable
import eu.transittrack.gtfs.model.FareAttributeTable
import eu.transittrack.gtfs.model.FareLegJoinRuleTable
import eu.transittrack.gtfs.model.FareLegRuleTable
import eu.transittrack.gtfs.model.FareMediaTable
import eu.transittrack.gtfs.model.FareProductTable
import eu.transittrack.gtfs.model.FareRuleTable
import eu.transittrack.gtfs.model.FareTransferRuleTable
import eu.transittrack.gtfs.model.FeedInfoTable
import eu.transittrack.gtfs.model.FrequencyTable
import eu.transittrack.gtfs.model.LevelTable
import eu.transittrack.gtfs.model.LocationGroupStopTable
import eu.transittrack.gtfs.model.LocationGroupTable
import eu.transittrack.gtfs.model.LocationTable
import eu.transittrack.gtfs.model.NetworkTable
import eu.transittrack.gtfs.model.PathwayTable
import eu.transittrack.gtfs.model.RevisionScoped
import eu.transittrack.gtfs.model.RevisionScopedTable
import eu.transittrack.gtfs.model.RiderCategoryTable
import eu.transittrack.gtfs.model.RouteNetworkTable
import eu.transittrack.gtfs.model.RouteTable
import eu.transittrack.gtfs.model.ShapePointTable
import eu.transittrack.gtfs.model.ShapeTable
import eu.transittrack.gtfs.model.StopAreaTable
import eu.transittrack.gtfs.model.StopTable
import eu.transittrack.gtfs.model.StopTimeTable
import eu.transittrack.gtfs.model.TimeframeTable
import eu.transittrack.gtfs.model.TransferTable
import eu.transittrack.gtfs.model.TranslationTable
import eu.transittrack.gtfs.model.TripTable

/**
 * Bulk write path for revision-scoped GTFS rows.
 *
 * Implementations write via a JDBC-batched insert path and can wipe every row belonging to a single
 * revision — used to clean up a FAILED revision or to re-ingest.
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
 * Ordered children/leaf tables first so the `DELETE`s never trip a FK. All FKs currently point only
 * at `gtfs_revision`, but this order is also what validation and pruning expect.
 *
 * Deliberately excludes `gtfs_revision` and `gtfs_feed` (not revision-scoped rows). Cross-checked
 * against `git grep '@Table(name' in `eu.transittrack.gtfs.model` (33 tables). Also the registry
 * [ExposedRevisionWriter] routes rows by: every row class written through it needs its table here.
 */
object GtfsTables {
    val ALL: List<RevisionScopedTable<*>> =
        listOf(
            StopTimeTable,
            ShapePointTable,
            ShapeTable,
            FrequencyTable,
            TransferTable,
            TripTable,
            RouteNetworkTable,
            RouteTable,
            StopAreaTable,
            LocationGroupStopTable,
            LocationGroupTable,
            LocationTable,
            PathwayTable,
            LevelTable,
            BookingRuleTable,
            FareLegJoinRuleTable,
            FareTransferRuleTable,
            FareLegRuleTable,
            FareRuleTable,
            FareAttributeTable,
            FareProductTable,
            FareMediaTable,
            RiderCategoryTable,
            TimeframeTable,
            AreaTable,
            NetworkTable,
            CalendarDateTable,
            CalendarTable,
            StopTable,
            AgencyTable,
            FeedInfoTable,
            TranslationTable,
            AttributionTable,
        )
}
