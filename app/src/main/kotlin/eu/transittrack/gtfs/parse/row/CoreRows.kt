package eu.transittrack.gtfs.parse.row

import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

import eu.transittrack.gtfs.parse.GtfsRecord

data class AgencyRow(
    val line: Long,
    val agencyId: String?,
    val agencyName: String?,
    val agencyUrl: String?,
    val agencyTimezone: ZoneId?,
    val agencyLang: Locale?,
    val agencyPhone: String?,
    val agencyFareUrl: String?,
    val agencyEmail: String?,
) {
    companion object {
        fun from(r: GtfsRecord) =
            AgencyRow(
                line = r.line,
                agencyId = r.string("agency_id"),
                agencyName = r.string("agency_name"),
                agencyUrl = r.string("agency_url"),
                agencyTimezone = r.timezone("agency_timezone"),
                agencyLang = r.locale("agency_lang"),
                agencyPhone = r.string("agency_phone"),
                agencyFareUrl = r.string("agency_fare_url"),
                agencyEmail = r.string("agency_email"),
            )
    }
}

data class StopRow(
    val line: Long,
    val stopId: String?,
    val stopCode: String?,
    val stopName: String?,
    val ttsStopName: String?,
    val stopDesc: String?,
    val stopLat: Double?,
    val stopLon: Double?,
    val zoneId: String?,
    val stopUrl: String?,
    val locationType: Int?,
    val parentStation: String?,
    val stopTimezone: ZoneId?,
    val wheelchairBoarding: Int?,
    val levelId: String?,
    val platformCode: String?,
) {
    companion object {
        fun from(r: GtfsRecord) =
            StopRow(
                line = r.line,
                stopId = r.string("stop_id"),
                stopCode = r.string("stop_code"),
                stopName = r.string("stop_name"),
                ttsStopName = r.string("tts_stop_name"),
                stopDesc = r.string("stop_desc"),
                stopLat = r.double("stop_lat"),
                stopLon = r.double("stop_lon"),
                zoneId = r.string("zone_id"),
                stopUrl = r.string("stop_url"),
                locationType = r.int("location_type"),
                parentStation = r.string("parent_station"),
                stopTimezone = r.timezone("stop_timezone"),
                wheelchairBoarding = r.int("wheelchair_boarding"),
                levelId = r.string("level_id"),
                platformCode = r.string("platform_code"),
            )
    }
}

data class RouteRow(
    val line: Long,
    val routeId: String?,
    val agencyId: String?,
    val routeShortName: String?,
    val routeLongName: String?,
    val routeDesc: String?,
    val routeType: Int?,
    val routeUrl: String?,
    val routeColor: String?,
    val routeTextColor: String?,
    val routeSortOrder: Int?,
    val continuousPickup: Int?,
    val continuousDropOff: Int?,
    val networkId: String?,
) {
    companion object {
        fun from(r: GtfsRecord) =
            RouteRow(
                line = r.line,
                routeId = r.string("route_id"),
                agencyId = r.string("agency_id"),
                routeShortName = r.string("route_short_name"),
                routeLongName = r.string("route_long_name"),
                routeDesc = r.string("route_desc"),
                routeType = r.int("route_type"),
                routeUrl = r.string("route_url"),
                routeColor = r.color("route_color"),
                routeTextColor = r.color("route_text_color"),
                routeSortOrder = r.int("route_sort_order"),
                continuousPickup = r.int("continuous_pickup"),
                continuousDropOff = r.int("continuous_drop_off"),
                networkId = r.string("network_id"),
            )
    }
}

data class TripRow(
    val line: Long,
    val routeId: String?,
    val serviceId: String?,
    val tripId: String?,
    val tripHeadsign: String?,
    val tripShortName: String?,
    val directionId: Int?,
    val blockId: String?,
    val shapeId: String?,
    val wheelchairAccessible: Int?,
    val bikesAllowed: Int?,
) {
    companion object {
        fun from(r: GtfsRecord) =
            TripRow(
                line = r.line,
                routeId = r.string("route_id"),
                serviceId = r.string("service_id"),
                tripId = r.string("trip_id"),
                tripHeadsign = r.string("trip_headsign"),
                tripShortName = r.string("trip_short_name"),
                directionId = r.int("direction_id"),
                blockId = r.string("block_id"),
                shapeId = r.string("shape_id"),
                wheelchairAccessible = r.int("wheelchair_accessible"),
                bikesAllowed = r.int("bikes_allowed"),
            )
    }
}

data class StopTimeRow(
    val line: Long,
    val tripId: String?,
    val stopSequence: Int?,
    val stopId: String?,
    val arrivalTime: Int?,
    val departureTime: Int?,
    val locationGroupId: String?,
    val locationId: String?,
    val stopHeadsign: String?,
    val startPickupDropOffWindow: Int?,
    val endPickupDropOffWindow: Int?,
    val pickupType: Int?,
    val dropOffType: Int?,
    val continuousPickup: Int?,
    val continuousDropOff: Int?,
    val shapeDistTraveled: Double?,
    val timepoint: Int?,
    val pickupBookingRuleId: String?,
    val dropOffBookingRuleId: String?,
) {
    companion object {
        fun from(r: GtfsRecord) =
            StopTimeRow(
                line = r.line,
                tripId = r.string("trip_id"),
                stopSequence = r.int("stop_sequence"),
                stopId = r.string("stop_id"),
                arrivalTime = r.time("arrival_time"),
                departureTime = r.time("departure_time"),
                locationGroupId = r.string("location_group_id"),
                locationId = r.string("location_id"),
                stopHeadsign = r.string("stop_headsign"),
                startPickupDropOffWindow = r.time("start_pickup_drop_off_window"),
                endPickupDropOffWindow = r.time("end_pickup_drop_off_window"),
                pickupType = r.int("pickup_type"),
                dropOffType = r.int("drop_off_type"),
                continuousPickup = r.int("continuous_pickup"),
                continuousDropOff = r.int("continuous_drop_off"),
                shapeDistTraveled = r.double("shape_dist_traveled"),
                timepoint = r.int("timepoint"),
                pickupBookingRuleId = r.string("pickup_booking_rule_id"),
                dropOffBookingRuleId = r.string("drop_off_booking_rule_id"),
            )
    }
}

data class CalendarRow(
    val line: Long,
    val monday: Int?,
    val tuesday: Int?,
    val wednesday: Int?,
    val thursday: Int?,
    val friday: Int?,
    val saturday: Int?,
    val sunday: Int?,
    val serviceId: String?,
    val startDate: LocalDate?,
    val endDate: LocalDate?,
) {
    companion object {
        fun from(r: GtfsRecord) =
            CalendarRow(
                line = r.line,
                monday = r.int("monday"),
                tuesday = r.int("tuesday"),
                wednesday = r.int("wednesday"),
                thursday = r.int("thursday"),
                friday = r.int("friday"),
                saturday = r.int("saturday"),
                sunday = r.int("sunday"),
                serviceId = r.string("service_id"),
                startDate = r.date("start_date"),
                endDate = r.date("end_date"),
            )
    }
}

data class CalendarDateRow(
    val line: Long,
    val serviceId: String?,
    val date: LocalDate?,
    val exceptionType: Int?,
) {
    companion object {
        fun from(r: GtfsRecord) =
            CalendarDateRow(
                line = r.line,
                serviceId = r.string("service_id"),
                date = r.date("date"),
                exceptionType = r.int("exception_type"),
            )
    }
}

data class FeedInfoRow(
    val line: Long,
    val feedPublisherName: String?,
    val feedPublisherUrl: String?,
    val feedLang: String?,
    val defaultLang: String?,
    val feedStartDate: LocalDate?,
    val feedEndDate: LocalDate?,
    val feedVersion: String?,
    val feedContactEmail: String?,
    val feedContactUrl: String?,
) {
    companion object {
        fun from(r: GtfsRecord) =
            FeedInfoRow(
                line = r.line,
                feedPublisherName = r.string("feed_publisher_name"),
                feedPublisherUrl = r.string("feed_publisher_url"),
                feedLang = r.language("feed_lang"),
                defaultLang = r.language("default_lang"),
                feedStartDate = r.date("feed_start_date"),
                feedEndDate = r.date("feed_end_date"),
                feedVersion = r.string("feed_version"),
                feedContactEmail = r.string("feed_contact_email"),
                feedContactUrl = r.string("feed_contact_url"),
            )
    }
}

data class ShapeRow(
    val line: Long,
    val shapeId: String?,
    val shapePtLat: Double?,
    val shapePtLon: Double?,
    val shapePtSequence: Int?,
    val shapeDistTraveled: Double?,
) {
    companion object {
        fun from(r: GtfsRecord) =
            ShapeRow(
                line = r.line,
                shapeId = r.string("shape_id"),
                shapePtLat = r.double("shape_pt_lat"),
                shapePtLon = r.double("shape_pt_lon"),
                shapePtSequence = r.int("shape_pt_sequence"),
                shapeDistTraveled = r.double("shape_dist_traveled"),
            )
    }
}
