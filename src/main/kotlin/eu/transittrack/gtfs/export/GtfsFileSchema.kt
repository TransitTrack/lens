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
    )
}
