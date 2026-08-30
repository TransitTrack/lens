package eu.transittrack.gtfs.parse.mapper

import eu.transittrack.gtfs.model.*
import eu.transittrack.gtfs.parse.GtfsRow

fun mapFrequency(rev: Long, r: GtfsRow) = GtfsFrequency(
    revisionId = rev,
    tripId = r.str("trip_id") ?: error("frequencies.txt row missing trip_id"),
    startTime = r.seconds("start_time") ?: error("frequencies.txt row missing start_time"),
    endTime = r.seconds("end_time"),
    headwaySecs = r.int("headway_secs"),
    exactTimes = r.int("exact_times"),
)

fun mapTransfer(rev: Long, r: GtfsRow) = GtfsTransfer(
    revisionId = rev,
    fromStopId = r.str("from_stop_id"),
    toStopId = r.str("to_stop_id"),
    fromRouteId = r.str("from_route_id"),
    toRouteId = r.str("to_route_id"),
    fromTripId = r.str("from_trip_id"),
    toTripId = r.str("to_trip_id"),
    transferType = r.int("transfer_type"),
    minTransferTime = r.int("min_transfer_time"),
)

fun mapTranslation(rev: Long, r: GtfsRow) = GtfsTranslation(
    revisionId = rev,
    tableName = r.str("table_name") ?: error("translations.txt row missing table_name"),
    fieldName = r.str("field_name") ?: error("translations.txt row missing field_name"),
    language = r.str("language") ?: error("translations.txt row missing language"),
    translation = r.str("translation"),
    recordId = r.str("record_id"),
    recordSubId = r.str("record_sub_id"),
    fieldValue = r.str("field_value"),
)

fun mapAttribution(rev: Long, r: GtfsRow) = GtfsAttribution(
    revisionId = rev,
    attributionId = r.str("attribution_id"),
    agencyId = r.str("agency_id"),
    routeId = r.str("route_id"),
    tripId = r.str("trip_id"),
    organizationName = r.str("organization_name") ?: error("attributions.txt row missing organization_name"),
    isProducer = r.int("is_producer"),
    isOperator = r.int("is_operator"),
    isAuthority = r.int("is_authority"),
    attributionUrl = r.str("attribution_url"),
    attributionEmail = r.str("attribution_email"),
    attributionPhone = r.str("attribution_phone"),
)
