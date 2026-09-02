package eu.transittrack.gtfs.parse.mapper

import eu.transittrack.gtfs.model.*
import org.mobilitydata.gtfsvalidator.table.GtfsAgency as VAgency
import org.mobilitydata.gtfsvalidator.table.GtfsCalendar as VCalendar
import org.mobilitydata.gtfsvalidator.table.GtfsCalendarDate as VCalendarDate
import org.mobilitydata.gtfsvalidator.table.GtfsFeedInfo as VFeedInfo
import org.mobilitydata.gtfsvalidator.table.GtfsRoute as VRoute
import org.mobilitydata.gtfsvalidator.table.GtfsShape as VShape
import org.mobilitydata.gtfsvalidator.table.GtfsStop as VStop
import org.mobilitydata.gtfsvalidator.table.GtfsStopTime as VStopTime
import org.mobilitydata.gtfsvalidator.table.GtfsTrip as VTrip

fun mapAgency(rev: Long, r: VAgency) = GtfsAgency(
    revisionId = rev,
    agencyId = r.agencyId().takeIf { r.hasAgencyId() },
    agencyName = r.agencyName().takeIf { r.hasAgencyName() },
    agencyUrl = r.agencyUrl().takeIf { r.hasAgencyUrl() },
    agencyTimezone = r.agencyTimezone().takeIf { r.hasAgencyTimezone() },
    agencyLang = r.agencyLang().takeIf { r.hasAgencyLang() },
    agencyPhone = r.agencyPhone().takeIf { r.hasAgencyPhone() },
    agencyFareUrl = r.agencyFareUrl().takeIf { r.hasAgencyFareUrl() },
    agencyEmail = r.agencyEmail().takeIf { r.hasAgencyEmail() },
)

fun mapStop(rev: Long, r: VStop) = GtfsStop(
    revisionId = rev,
    stopId = r.stopId().takeIf { r.hasStopId() } ?: error("stops.txt row missing stop_id"),
    stopCode = r.stopCode().takeIf { r.hasStopCode() },
    stopName = r.stopName().takeIf { r.hasStopName() },
    ttsStopName = r.ttsStopName().takeIf { r.hasTtsStopName() },
    stopDesc = r.stopDesc().takeIf { r.hasStopDesc() },
    stopLat = r.stopLat().takeIf { r.hasStopLat() },
    stopLon = r.stopLon().takeIf { r.hasStopLon() },
    zoneId = r.zoneId().takeIf { r.hasZoneId() },
    stopUrl = r.stopUrl().takeIf { r.hasStopUrl() },
    locationType = r.locationTypeValue().takeIf { r.hasLocationType() },
    parentStation = r.parentStation().takeIf { r.hasParentStation() },
    stopTimezone = r.stopTimezone().takeIf { r.hasStopTimezone() },
    wheelchairBoarding = r.wheelchairBoardingValue().takeIf { r.hasWheelchairBoarding() },
    levelId = r.levelId().takeIf { r.hasLevelId() },
    platformCode = r.platformCode().takeIf { r.hasPlatformCode() },
)

fun mapRoute(rev: Long, r: VRoute) = GtfsRoute(
    revisionId = rev,
    routeId = r.routeId().takeIf { r.hasRouteId() } ?: error("routes.txt row missing route_id"),
    agencyId = r.agencyId().takeIf { r.hasAgencyId() },
    routeShortName = r.routeShortName().takeIf { r.hasRouteShortName() },
    routeLongName = r.routeLongName().takeIf { r.hasRouteLongName() },
    routeDesc = r.routeDesc().takeIf { r.hasRouteDesc() },
    routeType = r.routeTypeValue().takeIf { r.hasRouteType() },
    routeUrl = r.routeUrl().takeIf { r.hasRouteUrl() },
    routeColor = r.routeColor().takeIf { r.hasRouteColor() }.toHex(),
    routeTextColor = r.routeTextColor().takeIf { r.hasRouteTextColor() }.toHex(),
    routeSortOrder = r.routeSortOrder().takeIf { r.hasRouteSortOrder() },
    continuousPickup = r.continuousPickupValue().takeIf { r.hasContinuousPickup() },
    continuousDropOff = r.continuousDropOffValue().takeIf { r.hasContinuousDropOff() },
    networkId = r.networkId().takeIf { r.hasNetworkId() },
)

fun mapTrip(rev: Long, r: VTrip) = GtfsTrip(
    revisionId = rev,
    routeId = r.routeId().takeIf { r.hasRouteId() } ?: error("trips.txt row missing route_id"),
    serviceId = r.serviceId().takeIf { r.hasServiceId() } ?: error("trips.txt row missing service_id"),
    tripId = r.tripId().takeIf { r.hasTripId() } ?: error("trips.txt row missing trip_id"),
    tripHeadsign = r.tripHeadsign().takeIf { r.hasTripHeadsign() },
    tripShortName = r.tripShortName().takeIf { r.hasTripShortName() },
    directionId = r.directionIdValue().takeIf { r.hasDirectionId() },
    blockId = r.blockId().takeIf { r.hasBlockId() },
    shapeId = r.shapeId().takeIf { r.hasShapeId() },
    wheelchairAccessible = r.wheelchairAccessibleValue().takeIf { r.hasWheelchairAccessible() },
    bikesAllowed = r.bikesAllowedValue().takeIf { r.hasBikesAllowed() },
)

fun mapStopTime(rev: Long, r: VStopTime) = GtfsStopTime(
    revisionId = rev,
    tripId = r.tripId().takeIf { r.hasTripId() } ?: error("stop_times.txt row missing trip_id"),
    stopSequence = r.stopSequence().takeIf { r.hasStopSequence() }
        ?: error("stop_times.txt row missing stop_sequence"),
    stopId = r.stopId().takeIf { r.hasStopId() },
    arrivalTime = r.arrivalTime().takeIf { r.hasArrivalTime() }.toSeconds(),
    departureTime = r.departureTime().takeIf { r.hasDepartureTime() }.toSeconds(),
    locationGroupId = r.locationGroupId().takeIf { r.hasLocationGroupId() },
    locationId = r.locationId().takeIf { r.hasLocationId() },
    stopHeadsign = r.stopHeadsign().takeIf { r.hasStopHeadsign() },
    startPickupDropOffWindow = r.startPickupDropOffWindow().takeIf { r.hasStartPickupDropOffWindow() }.toSeconds(),
    endPickupDropOffWindow = r.endPickupDropOffWindow().takeIf { r.hasEndPickupDropOffWindow() }.toSeconds(),
    pickupType = r.pickupTypeValue().takeIf { r.hasPickupType() },
    dropOffType = r.dropOffTypeValue().takeIf { r.hasDropOffType() },
    continuousPickup = r.continuousPickupValue().takeIf { r.hasContinuousPickup() },
    continuousDropOff = r.continuousDropOffValue().takeIf { r.hasContinuousDropOff() },
    shapeDistTraveled = r.shapeDistTraveled().takeIf { r.hasShapeDistTraveled() },
    timepoint = r.timepointValue().takeIf { r.hasTimepoint() },
    pickupBookingRuleId = r.pickupBookingRuleId().takeIf { r.hasPickupBookingRuleId() },
    dropOffBookingRuleId = r.dropOffBookingRuleId().takeIf { r.hasDropOffBookingRuleId() },
)

private fun calendarDay(has: Boolean, value: Int): Boolean? = value.takeIf { has }?.let { it == 1 }

fun mapCalendar(rev: Long, r: VCalendar) = GtfsCalendar(
    revisionId = rev,
    serviceId = r.serviceId().takeIf { r.hasServiceId() } ?: error("calendar.txt row missing service_id"),
    monday = calendarDay(r.hasMonday(), r.mondayValue()),
    tuesday = calendarDay(r.hasTuesday(), r.tuesdayValue()),
    wednesday = calendarDay(r.hasWednesday(), r.wednesdayValue()),
    thursday = calendarDay(r.hasThursday(), r.thursdayValue()),
    friday = calendarDay(r.hasFriday(), r.fridayValue()),
    saturday = calendarDay(r.hasSaturday(), r.saturdayValue()),
    sunday = calendarDay(r.hasSunday(), r.sundayValue()),
    startDate = r.startDate().takeIf { r.hasStartDate() }.toLocalDate(),
    endDate = r.endDate().takeIf { r.hasEndDate() }.toLocalDate(),
)

fun mapCalendarDate(rev: Long, r: VCalendarDate) = GtfsCalendarDate(
    revisionId = rev,
    serviceId = r.serviceId().takeIf { r.hasServiceId() } ?: error("calendar_dates.txt row missing service_id"),
    date = r.date().takeIf { r.hasDate() }.toLocalDate() ?: error("calendar_dates.txt row missing date"),
    exceptionType = r.exceptionTypeValue().takeIf { r.hasExceptionType() },
)

fun mapFeedInfo(rev: Long, r: VFeedInfo) = GtfsFeedInfo(
    revisionId = rev,
    feedPublisherName = r.feedPublisherName().takeIf { r.hasFeedPublisherName() },
    feedPublisherUrl = r.feedPublisherUrl().takeIf { r.hasFeedPublisherUrl() },
    feedLang = r.feedLang().takeIf { r.hasFeedLang() }.toLanguageTagOrNull(),
    defaultLang = r.defaultLang().takeIf { r.hasDefaultLang() }.toLanguageTagOrNull(),
    feedStartDate = r.feedStartDate().takeIf { r.hasFeedStartDate() }.toLocalDate(),
    feedEndDate = r.feedEndDate().takeIf { r.hasFeedEndDate() }.toLocalDate(),
    feedVersion = r.feedVersion().takeIf { r.hasFeedVersion() },
    feedContactEmail = r.feedContactEmail().takeIf { r.hasFeedContactEmail() },
    feedContactUrl = r.feedContactUrl().takeIf { r.hasFeedContactUrl() },
)

fun mapShapePoint(rev: Long, r: VShape) = GtfsShapePoint(
    revisionId = rev,
    shapeId = r.shapeId().takeIf { r.hasShapeId() } ?: error("shapes.txt row missing shape_id"),
    shapePtLat = r.shapePtLat().takeIf { r.hasShapePtLat() },
    shapePtLon = r.shapePtLon().takeIf { r.hasShapePtLon() },
    shapePtSequence = r.shapePtSequence().takeIf { r.hasShapePtSequence() }
        ?: error("shapes.txt row missing shape_pt_sequence"),
    shapeDistTraveled = r.shapeDistTraveled().takeIf { r.hasShapeDistTraveled() },
)
