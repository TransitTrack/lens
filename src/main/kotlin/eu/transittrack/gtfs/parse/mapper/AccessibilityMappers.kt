package eu.transittrack.gtfs.parse.mapper

import eu.transittrack.gtfs.model.BookingRule
import eu.transittrack.gtfs.model.Level
import eu.transittrack.gtfs.model.LocationGroup
import eu.transittrack.gtfs.model.LocationGroupStop
import eu.transittrack.gtfs.model.Pathway
import eu.transittrack.gtfs.parse.MissingRequiredFieldException
import eu.transittrack.gtfs.parse.row.BookingRuleRow
import eu.transittrack.gtfs.parse.row.LevelRow
import eu.transittrack.gtfs.parse.row.LocationGroupRow
import eu.transittrack.gtfs.parse.row.LocationGroupStopRow
import eu.transittrack.gtfs.parse.row.PathwayRow

fun mapPathway(
    rev: Long,
    r: PathwayRow,
) = Pathway(
    revisionId = rev,
    pathwayId = r.pathwayId ?: throw MissingRequiredFieldException("pathways.txt", r.line, "pathway_id"),
    fromStopId = r.fromStopId,
    toStopId = r.toStopId,
    pathwayMode = r.pathwayMode,
    isBidirectional = r.isBidirectional,
    length = r.length,
    traversalTime = r.traversalTime,
    stairCount = r.stairCount,
    maxSlope = r.maxSlope,
    minWidth = r.minWidth,
    signpostedAs = r.signpostedAs,
    reversedSignpostedAs = r.reversedSignpostedAs,
)

fun mapLevel(
    rev: Long,
    r: LevelRow,
) = Level(
    revisionId = rev,
    levelId = r.levelId ?: throw MissingRequiredFieldException("levels.txt", r.line, "level_id"),
    levelIndex = r.levelIndex,
    levelName = r.levelName,
)

fun mapLocationGroup(
    rev: Long,
    r: LocationGroupRow,
) = LocationGroup(
    revisionId = rev,
    locationGroupId = r.locationGroupId
        ?: throw MissingRequiredFieldException("location_groups.txt", r.line, "location_group_id"),
    locationGroupName = r.locationGroupName,
)

fun mapLocationGroupStop(
    rev: Long,
    r: LocationGroupStopRow,
) = LocationGroupStop(
    revisionId = rev,
    locationGroupId = r.locationGroupId
        ?: throw MissingRequiredFieldException("location_group_stops.txt", r.line, "location_group_id"),
    stopId = r.stopId ?: throw MissingRequiredFieldException("location_group_stops.txt", r.line, "stop_id"),
)

fun mapBookingRule(
    rev: Long,
    r: BookingRuleRow,
) = BookingRule(
    revisionId = rev,
    bookingRuleId = r.bookingRuleId ?: throw MissingRequiredFieldException("booking_rules.txt", r.line, "booking_rule_id"),
    bookingType = r.bookingType,
    priorNoticeDurationMin = r.priorNoticeDurationMin,
    priorNoticeDurationMax = r.priorNoticeDurationMax,
    priorNoticeLastDay = r.priorNoticeLastDay,
    priorNoticeLastTime = r.priorNoticeLastTime,
    priorNoticeStartDay = r.priorNoticeStartDay,
    priorNoticeStartTime = r.priorNoticeStartTime,
    priorNoticeServiceId = r.priorNoticeServiceId,
    message = r.message,
    pickupMessage = r.pickupMessage,
    dropOffMessage = r.dropOffMessage,
    phoneNumber = r.phoneNumber,
    infoUrl = r.infoUrl,
    bookingUrl = r.bookingUrl,
)
