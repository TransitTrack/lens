package eu.transittrack.gtfs.parse

import eu.transittrack.gtfs.model.RevisionScoped
import eu.transittrack.gtfs.parse.GtfsFileDef.Kind
import eu.transittrack.gtfs.parse.mapper.mapAgency
import eu.transittrack.gtfs.parse.mapper.mapArea
import eu.transittrack.gtfs.parse.mapper.mapAttribution
import eu.transittrack.gtfs.parse.mapper.mapBookingRule
import eu.transittrack.gtfs.parse.mapper.mapCalendar
import eu.transittrack.gtfs.parse.mapper.mapCalendarDate
import eu.transittrack.gtfs.parse.mapper.mapFareAttribute
import eu.transittrack.gtfs.parse.mapper.mapFareLegJoinRule
import eu.transittrack.gtfs.parse.mapper.mapFareLegRule
import eu.transittrack.gtfs.parse.mapper.mapFareMedia
import eu.transittrack.gtfs.parse.mapper.mapFareProduct
import eu.transittrack.gtfs.parse.mapper.mapFareRule
import eu.transittrack.gtfs.parse.mapper.mapFareTransferRule
import eu.transittrack.gtfs.parse.mapper.mapFeedInfo
import eu.transittrack.gtfs.parse.mapper.mapFrequency
import eu.transittrack.gtfs.parse.mapper.mapLevel
import eu.transittrack.gtfs.parse.mapper.mapLocationGroup
import eu.transittrack.gtfs.parse.mapper.mapLocationGroupStop
import eu.transittrack.gtfs.parse.mapper.mapNetwork
import eu.transittrack.gtfs.parse.mapper.mapPathway
import eu.transittrack.gtfs.parse.mapper.mapRiderCategory
import eu.transittrack.gtfs.parse.mapper.mapRoute
import eu.transittrack.gtfs.parse.mapper.mapRouteNetwork
import eu.transittrack.gtfs.parse.mapper.mapShapePoint
import eu.transittrack.gtfs.parse.mapper.mapStop
import eu.transittrack.gtfs.parse.mapper.mapStopArea
import eu.transittrack.gtfs.parse.mapper.mapStopTime
import eu.transittrack.gtfs.parse.mapper.mapTimeframe
import eu.transittrack.gtfs.parse.mapper.mapTransfer
import eu.transittrack.gtfs.parse.mapper.mapTranslation
import eu.transittrack.gtfs.parse.mapper.mapTrip
import eu.transittrack.gtfs.parse.row.AgencyRow
import eu.transittrack.gtfs.parse.row.AreaRow
import eu.transittrack.gtfs.parse.row.AttributionRow
import eu.transittrack.gtfs.parse.row.BookingRuleRow
import eu.transittrack.gtfs.parse.row.CalendarDateRow
import eu.transittrack.gtfs.parse.row.CalendarRow
import eu.transittrack.gtfs.parse.row.FareAttributeRow
import eu.transittrack.gtfs.parse.row.FareLegJoinRuleRow
import eu.transittrack.gtfs.parse.row.FareLegRuleRow
import eu.transittrack.gtfs.parse.row.FareMediaRow
import eu.transittrack.gtfs.parse.row.FareProductRow
import eu.transittrack.gtfs.parse.row.FareRuleRow
import eu.transittrack.gtfs.parse.row.FareTransferRuleRow
import eu.transittrack.gtfs.parse.row.FeedInfoRow
import eu.transittrack.gtfs.parse.row.FrequencyRow
import eu.transittrack.gtfs.parse.row.LevelRow
import eu.transittrack.gtfs.parse.row.LocationGroupRow
import eu.transittrack.gtfs.parse.row.LocationGroupStopRow
import eu.transittrack.gtfs.parse.row.NetworkRow
import eu.transittrack.gtfs.parse.row.PathwayRow
import eu.transittrack.gtfs.parse.row.RiderCategoryRow
import eu.transittrack.gtfs.parse.row.RouteNetworkRow
import eu.transittrack.gtfs.parse.row.RouteRow
import eu.transittrack.gtfs.parse.row.ShapeRow
import eu.transittrack.gtfs.parse.row.StopAreaRow
import eu.transittrack.gtfs.parse.row.StopRow
import eu.transittrack.gtfs.parse.row.StopTimeRow
import eu.transittrack.gtfs.parse.row.TimeframeRow
import eu.transittrack.gtfs.parse.row.TransferRow
import eu.transittrack.gtfs.parse.row.TranslationRow
import eu.transittrack.gtfs.parse.row.TripRow

class GtfsFileDef(
    val fileName: String,
    val entityType: String,
    val required: Boolean,
    val order: Int,
    val kind: Kind,
    val map: (revisionId: Long, record: GtfsRecord) -> RevisionScoped,
) {
    enum class Kind {
        CSV,
        GEOJSON,
        SHAPES,
    }
}

object GtfsFileRegistry {
    private fun <R> csv(
        file: String,
        type: String,
        required: Boolean,
        order: Int,
        read: (GtfsRecord) -> R,
        map: (Long, R) -> RevisionScoped,
    ) = GtfsFileDef(file, type, required, order, Kind.CSV) { rev, record -> map(rev, read(record)) }

    private fun <R> shape(
        file: String,
        type: String,
        required: Boolean,
        order: Int,
        read: (GtfsRecord) -> R,
        map: (Long, R) -> RevisionScoped,
    ) = GtfsFileDef(file, type, required, order, Kind.SHAPES) { rev, record -> map(rev, read(record)) }

    private fun geojson(
        file: String,
        type: String,
        required: Boolean,
        order: Int,
    ) = GtfsFileDef(file, type, required, order, Kind.GEOJSON) { _, _ -> error("geojson handled by ingestion") }

    val defs: List<GtfsFileDef> =
        listOf(
            csv("feed_info.txt", "gtfs_feed_info", false, 0, FeedInfoRow::from, ::mapFeedInfo),
            csv("calendar.txt", "gtfs_calendar", false, 1, CalendarRow::from, ::mapCalendar),
            csv("calendar_dates.txt", "gtfs_calendar_date", false, 2, CalendarDateRow::from, ::mapCalendarDate),
            csv("agency.txt", "gtfs_agency", true, 10, AgencyRow::from, ::mapAgency),
            csv("levels.txt", "gtfs_level", false, 15, LevelRow::from, ::mapLevel),
            csv("stops.txt", "gtfs_stop", true, 20, StopRow::from, ::mapStop),
            csv("routes.txt", "gtfs_route", true, 30, RouteRow::from, ::mapRoute),
            csv("networks.txt", "gtfs_network", false, 31, NetworkRow::from, ::mapNetwork),
            csv("route_networks.txt", "gtfs_route_network", false, 32, RouteNetworkRow::from, ::mapRouteNetwork),
            csv("areas.txt", "gtfs_area", false, 33, AreaRow::from, ::mapArea),
            csv("stop_areas.txt", "gtfs_stop_area", false, 34, StopAreaRow::from, ::mapStopArea),
            csv("trips.txt", "gtfs_trip", true, 40, TripRow::from, ::mapTrip),
            shape("shapes.txt", "gtfs_shape_point", false, 45, ShapeRow::from, ::mapShapePoint),
            csv("stop_times.txt", "gtfs_stop_time", true, 50, StopTimeRow::from, ::mapStopTime),
            csv("frequencies.txt", "gtfs_frequency", false, 55, FrequencyRow::from, ::mapFrequency),
            csv("transfers.txt", "gtfs_transfer", false, 56, TransferRow::from, ::mapTransfer),
            csv("timeframes.txt", "gtfs_timeframe", false, 60, TimeframeRow::from, ::mapTimeframe),
            csv("rider_categories.txt", "gtfs_rider_category", false, 61, RiderCategoryRow::from, ::mapRiderCategory),
            csv("fare_media.txt", "gtfs_fare_media", false, 62, FareMediaRow::from, ::mapFareMedia),
            csv("fare_products.txt", "gtfs_fare_product", false, 63, FareProductRow::from, ::mapFareProduct),
            csv("fare_attributes.txt", "gtfs_fare_attribute", false, 64, FareAttributeRow::from, ::mapFareAttribute),
            csv("fare_rules.txt", "gtfs_fare_rule", false, 65, FareRuleRow::from, ::mapFareRule),
            csv("fare_leg_rules.txt", "gtfs_fare_leg_rule", false, 66, FareLegRuleRow::from, ::mapFareLegRule),
            csv(
                "fare_leg_join_rules.txt",
                "gtfs_fare_leg_join_rule",
                false,
                67,
                FareLegJoinRuleRow::from,
                ::mapFareLegJoinRule,
            ),
            csv(
                "fare_transfer_rules.txt",
                "gtfs_fare_transfer_rule",
                false,
                68,
                FareTransferRuleRow::from,
                ::mapFareTransferRule,
            ),
            csv("pathways.txt", "gtfs_pathway", false, 70, PathwayRow::from, ::mapPathway),
            csv("location_groups.txt", "gtfs_location_group", false, 71, LocationGroupRow::from, ::mapLocationGroup),
            csv(
                "location_group_stops.txt",
                "gtfs_location_group_stop",
                false,
                72,
                LocationGroupStopRow::from,
                ::mapLocationGroupStop,
            ),
            geojson("locations.geojson", "gtfs_location", false, 73),
            csv("booking_rules.txt", "gtfs_booking_rule", false, 74, BookingRuleRow::from, ::mapBookingRule),
            csv("translations.txt", "gtfs_translation", false, 85, TranslationRow::from, ::mapTranslation),
            csv("attributions.txt", "gtfs_attribution", false, 86, AttributionRow::from, ::mapAttribution),
        )

    private val byFile = defs.associateBy { it.fileName }

    fun forFile(name: String): GtfsFileDef? = byFile[name]

    val requiredFiles: Set<String> = defs.filter { it.required }.map { it.fileName }.toSet()

    val orderedForParsing: List<GtfsFileDef> = defs.sortedBy { it.order }
}
