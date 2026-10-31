package eu.transittrack.gtfs.parse.mapper

import eu.transittrack.gtfs.model.Attribution
import eu.transittrack.gtfs.model.Frequency
import eu.transittrack.gtfs.model.Transfer
import eu.transittrack.gtfs.model.Translation
import eu.transittrack.gtfs.parse.MissingRequiredFieldException
import eu.transittrack.gtfs.parse.row.AttributionRow
import eu.transittrack.gtfs.parse.row.FrequencyRow
import eu.transittrack.gtfs.parse.row.TransferRow
import eu.transittrack.gtfs.parse.row.TranslationRow

fun mapFrequency(
    rev: Long,
    r: FrequencyRow,
) = Frequency(
    revisionId = rev,
    tripId = r.tripId ?: throw MissingRequiredFieldException("frequencies.txt", r.line, "trip_id"),
    startTime = r.startTime ?: throw MissingRequiredFieldException("frequencies.txt", r.line, "start_time"),
    endTime = r.endTime,
    headwaySecs = r.headwaySecs,
    exactTimes = r.exactTimes,
)

fun mapTransfer(
    rev: Long,
    r: TransferRow,
) = Transfer(
    revisionId = rev,
    fromStopId = r.fromStopId,
    toStopId = r.toStopId,
    fromRouteId = r.fromRouteId,
    toRouteId = r.toRouteId,
    fromTripId = r.fromTripId,
    toTripId = r.toTripId,
    transferType = r.transferType,
    minTransferTime = r.minTransferTime,
)

fun mapTranslation(
    rev: Long,
    r: TranslationRow,
) = Translation(
    revisionId = rev,
    tableName = r.tableName ?: throw MissingRequiredFieldException("translations.txt", r.line, "table_name"),
    fieldName = r.fieldName ?: throw MissingRequiredFieldException("translations.txt", r.line, "field_name"),
    language = r.language ?: throw MissingRequiredFieldException("translations.txt", r.line, "language"),
    translation = r.translation,
    recordId = r.recordId,
    recordSubId = r.recordSubId,
    fieldValue = r.fieldValue,
)

fun mapAttribution(
    rev: Long,
    r: AttributionRow,
) = Attribution(
    revisionId = rev,
    attributionId = r.attributionId,
    agencyId = r.agencyId,
    routeId = r.routeId,
    tripId = r.tripId,
    organizationName = r.organizationName
        ?: throw MissingRequiredFieldException("attributions.txt", r.line, "organization_name"),
    isProducer = r.isProducer,
    isOperator = r.isOperator,
    isAuthority = r.isAuthority,
    attributionUrl = r.attributionUrl,
    attributionEmail = r.attributionEmail,
    attributionPhone = r.attributionPhone,
)
