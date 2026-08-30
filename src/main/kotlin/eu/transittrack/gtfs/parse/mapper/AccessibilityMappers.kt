package eu.transittrack.gtfs.parse.mapper

import eu.transittrack.gtfs.model.*
import eu.transittrack.gtfs.parse.GtfsRow

fun mapPathway(rev: Long, r: GtfsRow) = GtfsPathway(
    revisionId = rev,
    pathwayId = r.str("pathway_id") ?: error("pathways.txt row missing pathway_id"),
    fromStopId = r.str("from_stop_id"),
    toStopId = r.str("to_stop_id"),
    pathwayMode = r.int("pathway_mode"),
    isBidirectional = r.int("is_bidirectional"),
    length = r.double("length"),
    traversalTime = r.int("traversal_time"),
    stairCount = r.int("stair_count"),
    maxSlope = r.double("max_slope"),
    minWidth = r.double("min_width"),
    signpostedAs = r.str("signposted_as"),
    reversedSignpostedAs = r.str("reversed_signposted_as"),
)

fun mapLevel(rev: Long, r: GtfsRow) = GtfsLevel(
    revisionId = rev,
    levelId = r.str("level_id") ?: error("levels.txt row missing level_id"),
    levelIndex = r.double("level_index"),
    levelName = r.str("level_name"),
)

fun mapLocationGroup(rev: Long, r: GtfsRow) = GtfsLocationGroup(
    revisionId = rev,
    locationGroupId = r.str("location_group_id") ?: error("location_groups.txt row missing location_group_id"),
    locationGroupName = r.str("location_group_name"),
)

fun mapLocationGroupStop(rev: Long, r: GtfsRow) = GtfsLocationGroupStop(
    revisionId = rev,
    locationGroupId = r.str("location_group_id") ?: error("location_group_stops.txt row missing location_group_id"),
    stopId = r.str("stop_id") ?: error("location_group_stops.txt row missing stop_id"),
)

fun mapBookingRule(rev: Long, r: GtfsRow) = GtfsBookingRule(
    revisionId = rev,
    bookingRuleId = r.str("booking_rule_id") ?: error("booking_rules.txt row missing booking_rule_id"),
    bookingType = r.int("booking_type"),
    priorNoticeDurationMin = r.int("prior_notice_duration_min"),
    priorNoticeDurationMax = r.int("prior_notice_duration_max"),
    priorNoticeLastDay = r.int("prior_notice_last_day"),
    priorNoticeLastTime = r.seconds("prior_notice_last_time"),
    priorNoticeStartDay = r.int("prior_notice_start_day"),
    priorNoticeStartTime = r.seconds("prior_notice_start_time"),
    priorNoticeServiceId = r.str("prior_notice_service_id"),
    message = r.str("message"),
    pickupMessage = r.str("pickup_message"),
    dropOffMessage = r.str("drop_off_message"),
    phoneNumber = r.str("phone_number"),
    infoUrl = r.str("info_url"),
    bookingUrl = r.str("booking_url"),
)
