package eu.transittrack.gtfs.parse.mapper

import eu.transittrack.gtfs.model.*
import org.mobilitydata.gtfsvalidator.table.GtfsAttribution
import org.mobilitydata.gtfsvalidator.table.GtfsFrequency
import org.mobilitydata.gtfsvalidator.table.GtfsTransfer
import org.mobilitydata.gtfsvalidator.table.GtfsTranslation

fun mapFrequency(rev: Long, r: GtfsFrequency) = Frequency(
    revisionId = rev,
    tripId = r.tripId().takeIf { r.hasTripId() } ?: error("frequencies.txt row missing trip_id"),
    startTime = r.startTime().takeIf { r.hasStartTime() }.toSeconds()
        ?: error("frequencies.txt row missing start_time"),
    endTime = r.endTime().takeIf { r.hasEndTime() }.toSeconds(),
    headwaySecs = r.headwaySecs().takeIf { r.hasHeadwaySecs() },
    exactTimes = r.exactTimesValue().takeIf { r.hasExactTimes() },
)

fun mapTransfer(rev: Long, r: GtfsTransfer) = Transfer(
    revisionId = rev,
    fromStopId = r.fromStopId().takeIf { r.hasFromStopId() },
    toStopId = r.toStopId().takeIf { r.hasToStopId() },
    fromRouteId = r.fromRouteId().takeIf { r.hasFromRouteId() },
    toRouteId = r.toRouteId().takeIf { r.hasToRouteId() },
    fromTripId = r.fromTripId().takeIf { r.hasFromTripId() },
    toTripId = r.toTripId().takeIf { r.hasToTripId() },
    transferType = r.transferTypeValue().takeIf { r.hasTransferType() },
    minTransferTime = r.minTransferTime().takeIf { r.hasMinTransferTime() },
)

fun mapTranslation(rev: Long, r: GtfsTranslation) = Translation(
    revisionId = rev,
    tableName = r.tableName().takeIf { r.hasTableName() } ?: error("translations.txt row missing table_name"),
    fieldName = r.fieldName().takeIf { r.hasFieldName() } ?: error("translations.txt row missing field_name"),
    language = r.language().takeIf { r.hasLanguage() }.toLanguageTagOrNull()
        ?: error("translations.txt row missing language"),
    translation = r.translation().takeIf { r.hasTranslation() },
    recordId = r.recordId().takeIf { r.hasRecordId() },
    recordSubId = r.recordSubId().takeIf { r.hasRecordSubId() },
    fieldValue = r.fieldValue().takeIf { r.hasFieldValue() },
)

fun mapAttribution(rev: Long, r: GtfsAttribution) = Attribution(
    revisionId = rev,
    attributionId = r.attributionId().takeIf { r.hasAttributionId() },
    agencyId = r.agencyId().takeIf { r.hasAgencyId() },
    routeId = r.routeId().takeIf { r.hasRouteId() },
    tripId = r.tripId().takeIf { r.hasTripId() },
    organizationName = r.organizationName().takeIf { r.hasOrganizationName() }
        ?: error("attributions.txt row missing organization_name"),
    isProducer = r.isProducerValue().takeIf { r.hasIsProducer() },
    isOperator = r.isOperatorValue().takeIf { r.hasIsOperator() },
    isAuthority = r.isAuthorityValue().takeIf { r.hasIsAuthority() },
    attributionUrl = r.attributionUrl().takeIf { r.hasAttributionUrl() },
    attributionEmail = r.attributionEmail().takeIf { r.hasAttributionEmail() },
    attributionPhone = r.attributionPhone().takeIf { r.hasAttributionPhone() },
)
