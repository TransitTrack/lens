package eu.transittrack.gtfs.parse.mapper

import org.mobilitydata.gtfsvalidator.table.GtfsBookingRules
import org.mobilitydata.gtfsvalidator.table.GtfsLevel
import org.mobilitydata.gtfsvalidator.table.GtfsLocationGroupStops
import org.mobilitydata.gtfsvalidator.table.GtfsLocationGroups
import org.mobilitydata.gtfsvalidator.table.GtfsPathway

import eu.transittrack.gtfs.model.BookingRule
import eu.transittrack.gtfs.model.Level
import eu.transittrack.gtfs.model.LocationGroup
import eu.transittrack.gtfs.model.LocationGroupStop
import eu.transittrack.gtfs.model.Pathway

fun mapPathway(
    rev: Long,
    r: GtfsPathway,
) = Pathway(
    revisionId = rev,
    pathwayId = r.pathwayId().takeIf { r.hasPathwayId() } ?: error("pathways.txt row missing pathway_id"),
    fromStopId = r.fromStopId().takeIf { r.hasFromStopId() },
    toStopId = r.toStopId().takeIf { r.hasToStopId() },
    pathwayMode = r.pathwayModeValue().takeIf { r.hasPathwayMode() },
    isBidirectional = r.isBidirectionalValue().takeIf { r.hasIsBidirectional() },
    length = r.length().takeIf { r.hasLength() },
    traversalTime = r.traversalTime().takeIf { r.hasTraversalTime() },
    stairCount = r.stairCount().takeIf { r.hasStairCount() },
    maxSlope = r.maxSlope().takeIf { r.hasMaxSlope() },
    minWidth = r.minWidth().takeIf { r.hasMinWidth() },
    signpostedAs = r.signpostedAs().takeIf { r.hasSignpostedAs() },
    reversedSignpostedAs = r.reversedSignpostedAs().takeIf { r.hasReversedSignpostedAs() },
)

fun mapLevel(
    rev: Long,
    r: GtfsLevel,
) = Level(
    revisionId = rev,
    levelId = r.levelId().takeIf { r.hasLevelId() } ?: error("levels.txt row missing level_id"),
    levelIndex = r.levelIndex().takeIf { r.hasLevelIndex() },
    levelName = r.levelName().takeIf { r.hasLevelName() },
)

fun mapLocationGroup(
    rev: Long,
    r: GtfsLocationGroups,
) = LocationGroup(
    revisionId = rev,
    locationGroupId =
        r.locationGroupId().takeIf { r.hasLocationGroupId() }
            ?: error("location_groups.txt row missing location_group_id"),
    locationGroupName = r.locationGroupName().takeIf { r.hasLocationGroupName() },
)

fun mapLocationGroupStop(
    rev: Long,
    r: GtfsLocationGroupStops,
) = LocationGroupStop(
    revisionId = rev,
    locationGroupId =
        r.locationGroupId().takeIf { r.hasLocationGroupId() }
            ?: error("location_group_stops.txt row missing location_group_id"),
    stopId = r.stopId().takeIf { r.hasStopId() } ?: error("location_group_stops.txt row missing stop_id"),
)

fun mapBookingRule(
    rev: Long,
    r: GtfsBookingRules,
) = BookingRule(
    revisionId = rev,
    bookingRuleId =
        r.bookingRuleId().takeIf { r.hasBookingRuleId() } ?: error("booking_rules.txt row missing booking_rule_id"),
    bookingType = r.bookingTypeValue().takeIf { r.hasBookingType() },
    priorNoticeDurationMin = r.priorNoticeDurationMin().takeIf { r.hasPriorNoticeDurationMin() },
    priorNoticeDurationMax = r.priorNoticeDurationMax().takeIf { r.hasPriorNoticeDurationMax() },
    priorNoticeLastDay = r.priorNoticeLastDay().takeIf { r.hasPriorNoticeLastDay() },
    priorNoticeLastTime = r.priorNoticeLastTime().takeIf { r.hasPriorNoticeLastTime() }.toSeconds(),
    priorNoticeStartDay = r.priorNoticeStartDay().takeIf { r.hasPriorNoticeStartDay() },
    priorNoticeStartTime = r.priorNoticeStartTime().takeIf { r.hasPriorNoticeStartTime() }.toSeconds(),
    priorNoticeServiceId = r.priorNoticeServiceId().takeIf { r.hasPriorNoticeServiceId() },
    message = r.message().takeIf { r.hasMessage() },
    pickupMessage = r.pickupMessage().takeIf { r.hasPickupMessage() },
    dropOffMessage = r.dropOffMessage().takeIf { r.hasDropOffMessage() },
    phoneNumber = r.phoneNumber().takeIf { r.hasPhoneNumber() },
    infoUrl = r.infoUrl().takeIf { r.hasInfoUrl() },
    bookingUrl = r.bookingUrl().takeIf { r.hasBookingUrl() },
)
