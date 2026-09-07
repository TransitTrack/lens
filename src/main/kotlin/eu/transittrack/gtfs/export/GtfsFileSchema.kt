package eu.transittrack.gtfs.export

import java.time.LocalDate
import java.time.format.DateTimeFormatter

enum class ColKind { TEXT, INT, TIME, BOOL_INT, FLOAT, DATE, COLOR }

data class GtfsCol(
    val header: String,
    val sqlColumn: String,
    val kind: ColKind,
)

data class GtfsFile(
    val name: String,
    val table: String,
    val columns: List<GtfsCol>,
)

private val YMD = DateTimeFormatter.ofPattern("yyyyMMdd")

fun formatCell(
    value: Any?,
    kind: ColKind,
): String {
    if (value == null) return ""
    return when (kind) {
        ColKind.TEXT, ColKind.INT, ColKind.FLOAT -> {
            value.toString()
        }

        ColKind.TIME -> {
            val s = (value as Number).toInt()
            "%02d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
        }

        ColKind.BOOL_INT -> {
            if (value == true || value == 1) "1" else "0"
        }

        ColKind.DATE -> {
            (value as LocalDate).format(YMD)
        }

        ColKind.COLOR -> {
            value.toString().removePrefix("#")
        }
    }
}

object GtfsFileSchema {
    private fun c(
        header: String,
        sql: String = header,
        kind: ColKind = ColKind.TEXT,
    ) = GtfsCol(header, sql, kind)

    val FILES: List<GtfsFile> = listOf(
        GtfsFile(
            "agency.txt", "agencies",
            listOf(
                c("agency_id"), c("agency_name"), c("agency_url"), c("agency_timezone"),
                c("agency_lang"), c("agency_phone"), c("agency_fare_url"), c("agency_email"),
            ),
        ),
        GtfsFile(
            "stops.txt", "stops",
            listOf(
                c("stop_id"), c("stop_code"), c("stop_name"), c("tts_stop_name"), c("stop_desc"),
                c("stop_lat", "stop_lat", ColKind.FLOAT), c("stop_lon", "stop_lon", ColKind.FLOAT),
                c("zone_id"), c("stop_url"),
                c("location_type", "location_type", ColKind.INT), c("parent_station"),
                c("stop_timezone"), c("wheelchair_boarding", "wheelchair_boarding", ColKind.INT),
                c("level_id"), c("platform_code"),
            ),
        ),
        GtfsFile(
            "routes.txt", "routes",
            listOf(
                c("route_id"), c("agency_id"), c("route_short_name"), c("route_long_name"),
                c("route_desc"), c("route_type", "route_type", ColKind.INT), c("route_url"),
                c("route_color", "route_color", ColKind.COLOR), c("route_text_color", "route_text_color", ColKind.COLOR),
                c("route_sort_order", "route_sort_order", ColKind.INT),
                c("continuous_pickup", "continuous_pickup", ColKind.INT),
                c("continuous_drop_off", "continuous_drop_off", ColKind.INT), c("network_id"),
            ),
        ),
        GtfsFile(
            "trips.txt", "trips",
            listOf(
                c("route_id"), c("service_id"), c("trip_id"), c("trip_headsign"), c("trip_short_name"),
                c("direction_id", "direction_id", ColKind.INT), c("block_id"), c("shape_id"),
                c("wheelchair_accessible", "wheelchair_accessible", ColKind.INT),
                c("bikes_allowed", "bikes_allowed", ColKind.INT),
            ),
        ),
        GtfsFile(
            "stop_times.txt", "stop_times",
            listOf(
                c("trip_id"),
                c("arrival_time", "arrival_time", ColKind.TIME),
                c("departure_time", "departure_time", ColKind.TIME),
                c("stop_id"),
                c("stop_sequence", "stop_sequence", ColKind.INT),
                c("stop_headsign"),
                c("pickup_type", "pickup_type", ColKind.INT),
                c("drop_off_type", "drop_off_type", ColKind.INT),
                c("continuous_pickup", "continuous_pickup", ColKind.INT),
                c("continuous_drop_off", "continuous_drop_off", ColKind.INT),
                c("shape_dist_traveled", "shape_dist_traveled", ColKind.FLOAT),
                c("timepoint", "timepoint", ColKind.INT),
            ),
        ),
        GtfsFile(
            "calendar.txt", "calendars",
            listOf(
                c("service_id"),
                c("monday", "monday", ColKind.BOOL_INT), c("tuesday", "tuesday", ColKind.BOOL_INT),
                c("wednesday", "wednesday", ColKind.BOOL_INT), c("thursday", "thursday", ColKind.BOOL_INT),
                c("friday", "friday", ColKind.BOOL_INT), c("saturday", "saturday", ColKind.BOOL_INT),
                c("sunday", "sunday", ColKind.BOOL_INT),
                c("start_date", "start_date", ColKind.DATE), c("end_date", "end_date", ColKind.DATE),
            ),
        ),
        GtfsFile(
            "calendar_dates.txt", "calendar_dates",
            listOf(
                c("service_id"), c("date", "date", ColKind.DATE), c("exception_type", "exception_type", ColKind.INT),
            ),
        ),
        GtfsFile(
            "frequencies.txt", "frequencies",
            listOf(
                c("trip_id"),
                c("start_time", "start_time", ColKind.TIME), c("end_time", "end_time", ColKind.TIME),
                c("headway_secs", "headway_secs", ColKind.INT), c("exact_times", "exact_times", ColKind.INT),
            ),
        ),
        GtfsFile(
            "shapes.txt", "shape_points",
            listOf(
                c("shape_id"),
                c("shape_pt_lat", "shape_pt_lat", ColKind.FLOAT), c("shape_pt_lon", "shape_pt_lon", ColKind.FLOAT),
                c("shape_pt_sequence", "shape_pt_sequence", ColKind.INT),
                c("shape_dist_traveled", "shape_dist_traveled", ColKind.FLOAT),
            ),
        ),
        GtfsFile(
            "feed_info.txt", "feed_infos",
            listOf(
                c("feed_publisher_name"), c("feed_publisher_url"), c("feed_lang"), c("default_lang"),
                c("feed_start_date", "feed_start_date", ColKind.DATE), c("feed_end_date", "feed_end_date", ColKind.DATE),
                c("feed_version"), c("feed_contact_email"), c("feed_contact_url"),
            ),
        ),
        GtfsFile(
            "transfers.txt", "transfers",
            listOf(
                c("from_stop_id"), c("to_stop_id"), c("from_route_id"), c("to_route_id"),
                c("from_trip_id"), c("to_trip_id"),
                c("transfer_type", kind = ColKind.INT), c("min_transfer_time", kind = ColKind.INT),
            ),
        ),
        GtfsFile(
            "translations.txt", "translations",
            listOf(
                c("table_name"), c("field_name"), c("language"), c("translation"),
                c("record_id"), c("record_sub_id"), c("field_value"),
            ),
        ),
        GtfsFile(
            "attributions.txt", "attributions",
            listOf(
                c("attribution_id"), c("agency_id"), c("route_id"), c("trip_id"), c("organization_name"),
                c("is_producer", kind = ColKind.INT), c("is_operator", kind = ColKind.INT),
                c("is_authority", kind = ColKind.INT),
                c("attribution_url"), c("attribution_email"), c("attribution_phone"),
            ),
        ),
        GtfsFile(
            "levels.txt", "levels",
            listOf(
                c("level_id"), c("level_index", kind = ColKind.FLOAT), c("level_name"),
            ),
        ),
        GtfsFile(
            "pathways.txt", "pathways",
            listOf(
                c("pathway_id"), c("from_stop_id"), c("to_stop_id"),
                c("pathway_mode", kind = ColKind.INT), c("is_bidirectional", kind = ColKind.INT),
                c("length", kind = ColKind.FLOAT), c("traversal_time", kind = ColKind.INT),
                c("stair_count", kind = ColKind.INT), c("max_slope", kind = ColKind.FLOAT),
                c("min_width", kind = ColKind.FLOAT), c("signposted_as"), c("reversed_signposted_as"),
            ),
        ),
        GtfsFile(
            "location_groups.txt", "location_groups",
            listOf(
                c("location_group_id"), c("location_group_name"),
            ),
        ),
        GtfsFile(
            "location_group_stops.txt", "location_group_stops",
            listOf(
                c("location_group_id"), c("stop_id"),
            ),
        ),
        GtfsFile(
            "booking_rules.txt", "booking_rules",
            listOf(
                c("booking_rule_id"), c("booking_type", kind = ColKind.INT),
                c("prior_notice_duration_min", kind = ColKind.INT),
                c("prior_notice_duration_max", kind = ColKind.INT),
                c("prior_notice_last_day", kind = ColKind.INT),
                c("prior_notice_last_time", kind = ColKind.TIME),
                c("prior_notice_start_day", kind = ColKind.INT),
                c("prior_notice_start_time", kind = ColKind.TIME),
                c("prior_notice_service_id"), c("message"), c("pickup_message"), c("drop_off_message"),
                c("phone_number"), c("info_url"), c("booking_url"),
            ),
        ),
        GtfsFile(
            "areas.txt", "areas",
            listOf(
                c("area_id"), c("area_name"),
            ),
        ),
        GtfsFile(
            "stop_areas.txt", "stop_areas",
            listOf(
                c("area_id"), c("stop_id"),
            ),
        ),
        GtfsFile(
            "networks.txt", "networks",
            listOf(
                c("network_id"), c("network_name"),
            ),
        ),
        GtfsFile(
            "route_networks.txt", "route_networks",
            listOf(
                c("network_id"), c("route_id"),
            ),
        ),
        GtfsFile(
            "timeframes.txt", "timeframes",
            listOf(
                c("timeframe_group_id"),
                c("start_time", kind = ColKind.TIME), c("end_time", kind = ColKind.TIME),
                c("service_id"),
            ),
        ),
        GtfsFile(
            "rider_categories.txt", "rider_categories",
            listOf(
                c("rider_category_id"), c("rider_category_name"),
                c("is_default_fare_category", kind = ColKind.INT), c("eligibility_url"),
            ),
        ),
        GtfsFile(
            "fare_media.txt", "fare_media",
            listOf(
                c("fare_media_id"), c("fare_media_name"), c("fare_media_type", kind = ColKind.INT),
            ),
        ),
        GtfsFile(
            "fare_products.txt", "fare_products",
            listOf(
                c("fare_product_id"), c("fare_product_name"), c("rider_category_id"), c("fare_media_id"),
                c("amount", kind = ColKind.FLOAT), c("currency"),
            ),
        ),
        GtfsFile(
            "fare_attributes.txt", "fare_attributes",
            listOf(
                c("fare_id"), c("price", kind = ColKind.FLOAT), c("currency_type"),
                c("payment_method", kind = ColKind.INT), c("transfers", kind = ColKind.INT),
                c("agency_id"), c("transfer_duration", kind = ColKind.INT),
            ),
        ),
        GtfsFile(
            "fare_rules.txt", "fare_rules",
            listOf(
                c("fare_id"), c("route_id"), c("origin_id"), c("destination_id"), c("contains_id"),
            ),
        ),
        GtfsFile(
            "fare_leg_rules.txt", "fare_leg_rules",
            listOf(
                c("leg_group_id"), c("network_id"), c("from_area_id"), c("to_area_id"),
                c("from_timeframe_group_id"), c("to_timeframe_group_id"), c("fare_product_id"),
                c("rule_priority", kind = ColKind.INT),
            ),
        ),
        GtfsFile(
            "fare_leg_join_rules.txt", "fare_leg_join_rules",
            listOf(
                c("from_network_id"), c("to_network_id"), c("from_stop_id"), c("to_stop_id"),
            ),
        ),
        GtfsFile(
            "fare_transfer_rules.txt", "fare_transfer_rules",
            listOf(
                c("from_leg_group_id"), c("to_leg_group_id"),
                c("transfer_count", kind = ColKind.INT), c("duration_limit", kind = ColKind.INT),
                c("duration_limit_type", kind = ColKind.INT), c("fare_transfer_type", kind = ColKind.INT),
                c("fare_product_id"),
            ),
        ),
    )
}
