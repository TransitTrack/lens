package eu.transittrack.gtfs.parse.mapper

import eu.transittrack.gtfs.model.*
import eu.transittrack.gtfs.parse.GtfsRow

fun mapAgency(rev: Long, r: GtfsRow) = GtfsAgency(
    revisionId = rev,
    agencyId = r.str("agency_id"), agencyName = r.str("agency_name"), agencyUrl = r.str("agency_url"),
    agencyTimezone = r.str("agency_timezone"), agencyLang = r.str("agency_lang"),
    agencyPhone = r.str("agency_phone"), agencyFareUrl = r.str("agency_fare_url"),
    agencyEmail = r.str("agency_email"),
)

fun mapStop(rev: Long, r: GtfsRow) = GtfsStop(
    revisionId = rev, stopId = r.str("stop_id") ?: error("stops.txt row missing stop_id"),
    stopCode = r.str("stop_code"), stopName = r.str("stop_name"), ttsStopName = r.str("tts_stop_name"),
    stopDesc = r.str("stop_desc"), stopLat = r.double("stop_lat"), stopLon = r.double("stop_lon"),
    zoneId = r.str("zone_id"), stopUrl = r.str("stop_url"), locationType = r.int("location_type"),
    parentStation = r.str("parent_station"), stopTimezone = r.str("stop_timezone"),
    wheelchairBoarding = r.int("wheelchair_boarding"), levelId = r.str("level_id"),
    platformCode = r.str("platform_code"),
)

fun mapRoute(rev: Long, r: GtfsRow) = GtfsRoute(
    revisionId = rev, routeId = r.str("route_id") ?: error("routes.txt row missing route_id"),
    agencyId = r.str("agency_id"), routeShortName = r.str("route_short_name"),
    routeLongName = r.str("route_long_name"), routeDesc = r.str("route_desc"),
    routeType = r.int("route_type"), routeUrl = r.str("route_url"), routeColor = r.str("route_color"),
    routeTextColor = r.str("route_text_color"), routeSortOrder = r.int("route_sort_order"),
    continuousPickup = r.int("continuous_pickup"), continuousDropOff = r.int("continuous_drop_off"),
    networkId = r.str("network_id"),
)

fun mapTrip(rev: Long, r: GtfsRow) = GtfsTrip(
    revisionId = rev,
    routeId = r.str("route_id") ?: error("trips.txt row missing route_id"),
    serviceId = r.str("service_id") ?: error("trips.txt row missing service_id"),
    tripId = r.str("trip_id") ?: error("trips.txt row missing trip_id"),
    tripHeadsign = r.str("trip_headsign"), tripShortName = r.str("trip_short_name"),
    directionId = r.int("direction_id"), blockId = r.str("block_id"), shapeId = r.str("shape_id"),
    wheelchairAccessible = r.int("wheelchair_accessible"), bikesAllowed = r.int("bikes_allowed"),
)

fun mapStopTime(rev: Long, r: GtfsRow) = GtfsStopTime(
    revisionId = rev,
    tripId = r.str("trip_id") ?: error("stop_times.txt row missing trip_id"),
    stopSequence = r.int("stop_sequence") ?: error("stop_times.txt row missing stop_sequence"),
    stopId = r.str("stop_id"), arrivalTime = r.seconds("arrival_time"),
    departureTime = r.seconds("departure_time"), locationGroupId = r.str("location_group_id"),
    locationId = r.str("location_id"), stopHeadsign = r.str("stop_headsign"),
    startPickupDropOffWindow = r.seconds("start_pickup_drop_off_window"),
    endPickupDropOffWindow = r.seconds("end_pickup_drop_off_window"),
    pickupType = r.int("pickup_type"), dropOffType = r.int("drop_off_type"),
    continuousPickup = r.int("continuous_pickup"), continuousDropOff = r.int("continuous_drop_off"),
    shapeDistTraveled = r.double("shape_dist_traveled"), timepoint = r.int("timepoint"),
    pickupBookingRuleId = r.str("pickup_booking_rule_id"),
    dropOffBookingRuleId = r.str("drop_off_booking_rule_id"),
)

fun mapCalendar(rev: Long, r: GtfsRow) = GtfsCalendar(
    revisionId = rev, serviceId = r.str("service_id") ?: error("calendar.txt row missing service_id"),
    monday = r.bool01("monday"), tuesday = r.bool01("tuesday"), wednesday = r.bool01("wednesday"),
    thursday = r.bool01("thursday"), friday = r.bool01("friday"), saturday = r.bool01("saturday"),
    sunday = r.bool01("sunday"), startDate = r.date("start_date"), endDate = r.date("end_date"),
)

fun mapCalendarDate(rev: Long, r: GtfsRow) = GtfsCalendarDate(
    revisionId = rev, serviceId = r.str("service_id") ?: error("calendar_dates.txt row missing service_id"),
    date = r.date("date") ?: error("calendar_dates.txt row missing date"),
    exceptionType = r.int("exception_type"),
)

fun mapFeedInfo(rev: Long, r: GtfsRow) = GtfsFeedInfo(
    revisionId = rev, feedPublisherName = r.str("feed_publisher_name"),
    feedPublisherUrl = r.str("feed_publisher_url"), feedLang = r.str("feed_lang"),
    defaultLang = r.str("default_lang"), feedStartDate = r.date("feed_start_date"),
    feedEndDate = r.date("feed_end_date"), feedVersion = r.str("feed_version"),
    feedContactEmail = r.str("feed_contact_email"), feedContactUrl = r.str("feed_contact_url"),
)

fun mapShapePoint(rev: Long, r: GtfsRow) = GtfsShapePoint(
    revisionId = rev, shapeId = r.str("shape_id") ?: error("shapes.txt row missing shape_id"),
    shapePtLat = r.double("shape_pt_lat"), shapePtLon = r.double("shape_pt_lon"),
    shapePtSequence = r.int("shape_pt_sequence") ?: error("shapes.txt row missing shape_pt_sequence"),
    shapeDistTraveled = r.double("shape_dist_traveled"),
)
