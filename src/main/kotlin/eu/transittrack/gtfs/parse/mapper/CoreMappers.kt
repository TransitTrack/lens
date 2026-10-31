package eu.transittrack.gtfs.parse.mapper

import eu.transittrack.gtfs.model.Agency
import eu.transittrack.gtfs.model.Calendar
import eu.transittrack.gtfs.model.CalendarDate
import eu.transittrack.gtfs.model.FeedInfo
import eu.transittrack.gtfs.model.Route
import eu.transittrack.gtfs.model.ShapePoint
import eu.transittrack.gtfs.model.Stop
import eu.transittrack.gtfs.model.StopTime
import eu.transittrack.gtfs.model.Trip
import eu.transittrack.gtfs.parse.MissingRequiredFieldException
import eu.transittrack.gtfs.parse.row.AgencyRow
import eu.transittrack.gtfs.parse.row.CalendarDateRow
import eu.transittrack.gtfs.parse.row.CalendarRow
import eu.transittrack.gtfs.parse.row.FeedInfoRow
import eu.transittrack.gtfs.parse.row.RouteRow
import eu.transittrack.gtfs.parse.row.ShapeRow
import eu.transittrack.gtfs.parse.row.StopRow
import eu.transittrack.gtfs.parse.row.StopTimeRow
import eu.transittrack.gtfs.parse.row.TripRow

fun mapAgency(
    rev: Long,
    r: AgencyRow,
) = Agency(
    revisionId = rev,
    agencyId = r.agencyId,
    agencyName = r.agencyName,
    agencyUrl = r.agencyUrl,
    agencyTimezone = r.agencyTimezone,
    agencyLang = r.agencyLang,
    agencyPhone = r.agencyPhone,
    agencyFareUrl = r.agencyFareUrl,
    agencyEmail = r.agencyEmail,
)

fun mapStop(
    rev: Long,
    r: StopRow,
) = Stop(
    revisionId = rev,
    stopId = r.stopId ?: throw MissingRequiredFieldException("stops.txt", r.line, "stop_id"),
    stopCode = r.stopCode,
    stopName = r.stopName,
    ttsStopName = r.ttsStopName,
    stopDesc = r.stopDesc,
    stopLat = r.stopLat,
    stopLon = r.stopLon,
    zoneId = r.zoneId,
    stopUrl = r.stopUrl,
    locationType = r.locationType,
    parentStation = r.parentStation,
    stopTimezone = r.stopTimezone,
    wheelchairBoarding = r.wheelchairBoarding,
    levelId = r.levelId,
    platformCode = r.platformCode,
)

fun mapRoute(
    rev: Long,
    r: RouteRow,
) = Route(
    revisionId = rev,
    routeId = r.routeId ?: throw MissingRequiredFieldException("routes.txt", r.line, "route_id"),
    agencyId = r.agencyId,
    routeShortName = r.routeShortName,
    routeLongName = r.routeLongName,
    routeDesc = r.routeDesc,
    routeType = r.routeType,
    routeUrl = r.routeUrl,
    routeColor = r.routeColor,
    routeTextColor = r.routeTextColor,
    routeSortOrder = r.routeSortOrder,
    continuousPickup = r.continuousPickup,
    continuousDropOff = r.continuousDropOff,
    networkId = r.networkId,
)

fun mapTrip(
    rev: Long,
    r: TripRow,
) = Trip(
    revisionId = rev,
    routeId = r.routeId ?: throw MissingRequiredFieldException("trips.txt", r.line, "route_id"),
    serviceId = r.serviceId ?: throw MissingRequiredFieldException("trips.txt", r.line, "service_id"),
    tripId = r.tripId ?: throw MissingRequiredFieldException("trips.txt", r.line, "trip_id"),
    tripHeadsign = r.tripHeadsign,
    tripShortName = r.tripShortName,
    directionId = r.directionId,
    blockId = r.blockId,
    shapeId = r.shapeId,
    wheelchairAccessible = r.wheelchairAccessible,
    bikesAllowed = r.bikesAllowed,
)

fun mapStopTime(
    rev: Long,
    r: StopTimeRow,
) = StopTime(
    revisionId = rev,
    tripId = r.tripId ?: throw MissingRequiredFieldException("stop_times.txt", r.line, "trip_id"),
    stopSequence = r.stopSequence ?: throw MissingRequiredFieldException("stop_times.txt", r.line, "stop_sequence"),
    stopId = r.stopId,
    arrivalTime = r.arrivalTime,
    departureTime = r.departureTime,
    locationGroupId = r.locationGroupId,
    locationId = r.locationId,
    stopHeadsign = r.stopHeadsign,
    startPickupDropOffWindow = r.startPickupDropOffWindow,
    endPickupDropOffWindow = r.endPickupDropOffWindow,
    pickupType = r.pickupType,
    dropOffType = r.dropOffType,
    continuousPickup = r.continuousPickup,
    continuousDropOff = r.continuousDropOff,
    shapeDistTraveled = r.shapeDistTraveled,
    timepoint = r.timepoint,
    pickupBookingRuleId = r.pickupBookingRuleId,
    dropOffBookingRuleId = r.dropOffBookingRuleId,
)

private fun calendarDay(value: Int?): Boolean? = value?.let { it == 1 }

fun mapCalendar(
    rev: Long,
    r: CalendarRow,
) = Calendar(
    revisionId = rev,
    serviceId = r.serviceId ?: throw MissingRequiredFieldException("calendar.txt", r.line, "service_id"),
    monday = calendarDay(r.monday),
    tuesday = calendarDay(r.tuesday),
    wednesday = calendarDay(r.wednesday),
    thursday = calendarDay(r.thursday),
    friday = calendarDay(r.friday),
    saturday = calendarDay(r.saturday),
    sunday = calendarDay(r.sunday),
    startDate = r.startDate,
    endDate = r.endDate,
)

fun mapCalendarDate(
    rev: Long,
    r: CalendarDateRow,
) = CalendarDate(
    revisionId = rev,
    serviceId = r.serviceId ?: throw MissingRequiredFieldException("calendar_dates.txt", r.line, "service_id"),
    date = r.date ?: throw MissingRequiredFieldException("calendar_dates.txt", r.line, "date"),
    exceptionType = r.exceptionType,
)

fun mapFeedInfo(
    rev: Long,
    r: FeedInfoRow,
) = FeedInfo(
    revisionId = rev,
    feedPublisherName = r.feedPublisherName,
    feedPublisherUrl = r.feedPublisherUrl,
    feedLang = r.feedLang,
    defaultLang = r.defaultLang,
    feedStartDate = r.feedStartDate,
    feedEndDate = r.feedEndDate,
    feedVersion = r.feedVersion,
    feedContactEmail = r.feedContactEmail,
    feedContactUrl = r.feedContactUrl,
)

fun mapShapePoint(
    rev: Long,
    r: ShapeRow,
) = ShapePoint(
    revisionId = rev,
    shapeId = r.shapeId ?: throw MissingRequiredFieldException("shapes.txt", r.line, "shape_id"),
    shapePtLat = r.shapePtLat,
    shapePtLon = r.shapePtLon,
    shapePtSequence = r.shapePtSequence ?: throw MissingRequiredFieldException("shapes.txt", r.line, "shape_pt_sequence"),
    shapeDistTraveled = r.shapeDistTraveled,
)
