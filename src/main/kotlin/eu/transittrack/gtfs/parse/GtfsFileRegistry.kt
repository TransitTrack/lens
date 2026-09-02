package eu.transittrack.gtfs.parse

import eu.transittrack.gtfs.model.RevisionScoped
import eu.transittrack.gtfs.parse.GtfsFileDef.Kind
import eu.transittrack.gtfs.parse.mapper.*
import org.mobilitydata.gtfsvalidator.table.GtfsEntity
import org.mobilitydata.gtfsvalidator.table.GtfsShape

object GtfsFileRegistry {

    private inline fun <reified T : GtfsEntity> csv(
        file: String, type: String, required: Boolean, order: Int,
        crossinline map: (Long, T) -> RevisionScoped,
    ) = GtfsFileDef(file, type, required, order, Kind.CSV) { rev, e -> map(rev, e as T) }

    val defs: List<GtfsFileDef> = listOf(
        csv("agency.txt", "gtfs_agency", true, 10, ::mapAgency),
        csv("levels.txt", "gtfs_level", false, 15, ::mapLevel),
        csv("stops.txt", "gtfs_stop", true, 20, ::mapStop),
        csv("calendar.txt", "gtfs_calendar", false, 25, ::mapCalendar),
        csv("calendar_dates.txt", "gtfs_calendar_date", false, 26, ::mapCalendarDate),
        csv("routes.txt", "gtfs_route", true, 30, ::mapRoute),
        csv("networks.txt", "gtfs_network", false, 31, ::mapNetwork),
        csv("route_networks.txt", "gtfs_route_network", false, 32, ::mapRouteNetwork),
        csv("areas.txt", "gtfs_area", false, 33, ::mapArea),
        csv("stop_areas.txt", "gtfs_stop_area", false, 34, ::mapStopArea),
        csv("trips.txt", "gtfs_trip", true, 40, ::mapTrip),
        GtfsFileDef("shapes.txt", "gtfs_shape_point", false, 45, Kind.SHAPES) { rev, e ->
            mapShapePoint(rev, e as GtfsShape)
        },
        csv("stop_times.txt", "gtfs_stop_time", true, 50, ::mapStopTime),
        csv("frequencies.txt", "gtfs_frequency", false, 55, ::mapFrequency),
        csv("transfers.txt", "gtfs_transfer", false, 56, ::mapTransfer),
        csv("timeframes.txt", "gtfs_timeframe", false, 60, ::mapTimeframe),
        csv("rider_categories.txt", "gtfs_rider_category", false, 61, ::mapRiderCategory),
        csv("fare_media.txt", "gtfs_fare_media", false, 62, ::mapFareMedia),
        csv("fare_products.txt", "gtfs_fare_product", false, 63, ::mapFareProduct),
        csv("fare_attributes.txt", "gtfs_fare_attribute", false, 64, ::mapFareAttribute),
        csv("fare_rules.txt", "gtfs_fare_rule", false, 65, ::mapFareRule),
        csv("fare_leg_rules.txt", "gtfs_fare_leg_rule", false, 66, ::mapFareLegRule),
        csv("fare_leg_join_rules.txt", "gtfs_fare_leg_join_rule", false, 67, ::mapFareLegJoinRule),
        csv("fare_transfer_rules.txt", "gtfs_fare_transfer_rule", false, 68, ::mapFareTransferRule),
        csv("pathways.txt", "gtfs_pathway", false, 70, ::mapPathway),
        csv("location_groups.txt", "gtfs_location_group", false, 71, ::mapLocationGroup),
        csv("location_group_stops.txt", "gtfs_location_group_stop", false, 72, ::mapLocationGroupStop),
        GtfsFileDef("locations.geojson", "gtfs_location", false, 73, Kind.GEOJSON) { _, _ -> error("geojson handled by ingestion") },
        csv("booking_rules.txt", "gtfs_booking_rule", false, 74, ::mapBookingRule),
        csv("feed_info.txt", "gtfs_feed_info", false, 80, ::mapFeedInfo),
        csv("translations.txt", "gtfs_translation", false, 85, ::mapTranslation),
        csv("attributions.txt", "gtfs_attribution", false, 86, ::mapAttribution),
    )

    private val byFile = defs.associateBy { it.fileName }
    fun forFile(name: String): GtfsFileDef? = byFile[name]
    val requiredFiles: Set<String> = defs.filter { it.required }.map { it.fileName }.toSet()
    val orderedForParsing: List<GtfsFileDef> = defs.sortedBy { it.order }
}
