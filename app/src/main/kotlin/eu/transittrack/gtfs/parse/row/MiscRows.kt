package eu.transittrack.gtfs.parse.row

import eu.transittrack.gtfs.parse.GtfsRecord

data class FrequencyRow(
    val line: Long,
    val tripId: String?,
    val startTime: Int?,
    val endTime: Int?,
    val headwaySecs: Int?,
    val exactTimes: Int?,
) {
    companion object {
        fun from(r: GtfsRecord) =
            FrequencyRow(
                line = r.line,
                tripId = r.string("trip_id"),
                startTime = r.time("start_time"),
                endTime = r.time("end_time"),
                headwaySecs = r.int("headway_secs"),
                exactTimes = r.int("exact_times"),
            )
    }
}

data class TransferRow(
    val line: Long,
    val fromStopId: String?,
    val toStopId: String?,
    val fromRouteId: String?,
    val toRouteId: String?,
    val fromTripId: String?,
    val toTripId: String?,
    val transferType: Int?,
    val minTransferTime: Int?,
) {
    companion object {
        fun from(r: GtfsRecord) =
            TransferRow(
                line = r.line,
                fromStopId = r.string("from_stop_id"),
                toStopId = r.string("to_stop_id"),
                fromRouteId = r.string("from_route_id"),
                toRouteId = r.string("to_route_id"),
                fromTripId = r.string("from_trip_id"),
                toTripId = r.string("to_trip_id"),
                transferType = r.int("transfer_type"),
                minTransferTime = r.int("min_transfer_time"),
            )
    }
}

data class TranslationRow(
    val line: Long,
    val tableName: String?,
    val fieldName: String?,
    val language: String?,
    val translation: String?,
    val recordId: String?,
    val recordSubId: String?,
    val fieldValue: String?,
) {
    companion object {
        fun from(r: GtfsRecord) =
            TranslationRow(
                line = r.line,
                tableName = r.string("table_name"),
                fieldName = r.string("field_name"),
                language = r.language("language"),
                translation = r.string("translation"),
                recordId = r.string("record_id"),
                recordSubId = r.string("record_sub_id"),
                fieldValue = r.string("field_value"),
            )
    }
}

data class AttributionRow(
    val line: Long,
    val attributionId: String?,
    val agencyId: String?,
    val routeId: String?,
    val tripId: String?,
    val organizationName: String?,
    val isProducer: Int?,
    val isOperator: Int?,
    val isAuthority: Int?,
    val attributionUrl: String?,
    val attributionEmail: String?,
    val attributionPhone: String?,
) {
    companion object {
        fun from(r: GtfsRecord) =
            AttributionRow(
                line = r.line,
                attributionId = r.string("attribution_id"),
                agencyId = r.string("agency_id"),
                routeId = r.string("route_id"),
                tripId = r.string("trip_id"),
                organizationName = r.string("organization_name"),
                isProducer = r.int("is_producer"),
                isOperator = r.int("is_operator"),
                isAuthority = r.int("is_authority"),
                attributionUrl = r.string("attribution_url"),
                attributionEmail = r.string("attribution_email"),
                attributionPhone = r.string("attribution_phone"),
            )
    }
}
