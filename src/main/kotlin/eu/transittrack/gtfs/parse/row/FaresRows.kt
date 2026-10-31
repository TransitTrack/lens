package eu.transittrack.gtfs.parse.row

import eu.transittrack.gtfs.parse.GtfsRecord

data class FareAttributeRow(
    val line: Long,
    val fareId: String?,
    val price: Double?,
    val currencyType: String?,
    val paymentMethod: Int?,
    val transfers: Int?,
    val agencyId: String?,
    val transferDuration: Int?,
) {
    companion object {
        fun from(r: GtfsRecord) =
            FareAttributeRow(
                line = r.line,
                fareId = r.string("fare_id"),
                price = r.double("price"),
                currencyType = r.currency("currency_type"),
                paymentMethod = r.int("payment_method"),
                transfers = r.int("transfers"),
                agencyId = r.string("agency_id"),
                transferDuration = r.int("transfer_duration"),
            )
    }
}

data class FareRuleRow(
    val line: Long,
    val fareId: String?,
    val routeId: String?,
    val originId: String?,
    val destinationId: String?,
    val containsId: String?,
) {
    companion object {
        fun from(r: GtfsRecord) =
            FareRuleRow(
                line = r.line,
                fareId = r.string("fare_id"),
                routeId = r.string("route_id"),
                originId = r.string("origin_id"),
                destinationId = r.string("destination_id"),
                containsId = r.string("contains_id"),
            )
    }
}

data class TimeframeRow(
    val line: Long,
    val timeframeGroupId: String?,
    val startTime: Int?,
    val endTime: Int?,
    val serviceId: String?,
) {
    companion object {
        fun from(r: GtfsRecord) =
            TimeframeRow(
                line = r.line,
                timeframeGroupId = r.string("timeframe_group_id"),
                startTime = r.time("start_time"),
                endTime = r.time("end_time"),
                serviceId = r.string("service_id"),
            )
    }
}

data class RiderCategoryRow(
    val line: Long,
    val riderCategoryId: String?,
    val riderCategoryName: String?,
    val isDefaultFareCategory: Int?,
    val eligibilityUrl: String?,
) {
    companion object {
        fun from(r: GtfsRecord) =
            RiderCategoryRow(
                line = r.line,
                riderCategoryId = r.string("rider_category_id"),
                riderCategoryName = r.string("rider_category_name"),
                isDefaultFareCategory = r.int("is_default_fare_category"),
                eligibilityUrl = r.string("eligibility_url"),
            )
    }
}

data class FareMediaRow(
    val line: Long,
    val fareMediaId: String?,
    val fareMediaName: String?,
    val fareMediaType: Int?,
) {
    companion object {
        fun from(r: GtfsRecord) =
            FareMediaRow(
                line = r.line,
                fareMediaId = r.string("fare_media_id"),
                fareMediaName = r.string("fare_media_name"),
                fareMediaType = r.int("fare_media_type"),
            )
    }
}

data class FareProductRow(
    val line: Long,
    val fareProductId: String?,
    val fareProductName: String?,
    val riderCategoryId: String?,
    val fareMediaId: String?,
    val amount: Double?,
    val currency: String?,
) {
    companion object {
        fun from(r: GtfsRecord) =
            FareProductRow(
                line = r.line,
                fareProductId = r.string("fare_product_id"),
                fareProductName = r.string("fare_product_name"),
                riderCategoryId = r.string("rider_category_id"),
                fareMediaId = r.string("fare_media_id"),
                amount = r.double("amount"),
                currency = r.currency("currency"),
            )
    }
}

data class FareLegRuleRow(
    val line: Long,
    val legGroupId: String?,
    val networkId: String?,
    val fromAreaId: String?,
    val toAreaId: String?,
    val fromTimeframeGroupId: String?,
    val toTimeframeGroupId: String?,
    val fareProductId: String?,
    val rulePriority: Int?,
) {
    companion object {
        fun from(r: GtfsRecord) =
            FareLegRuleRow(
                line = r.line,
                legGroupId = r.string("leg_group_id"),
                networkId = r.string("network_id"),
                fromAreaId = r.string("from_area_id"),
                toAreaId = r.string("to_area_id"),
                fromTimeframeGroupId = r.string("from_timeframe_group_id"),
                toTimeframeGroupId = r.string("to_timeframe_group_id"),
                fareProductId = r.string("fare_product_id"),
                rulePriority = r.int("rule_priority"),
            )
    }
}

data class FareLegJoinRuleRow(
    val line: Long,
    val fromNetworkId: String?,
    val toNetworkId: String?,
    val fromStopId: String?,
    val toStopId: String?,
) {
    companion object {
        fun from(r: GtfsRecord) =
            FareLegJoinRuleRow(
                line = r.line,
                fromNetworkId = r.string("from_network_id"),
                toNetworkId = r.string("to_network_id"),
                fromStopId = r.string("from_stop_id"),
                toStopId = r.string("to_stop_id"),
            )
    }
}

data class FareTransferRuleRow(
    val line: Long,
    val fromLegGroupId: String?,
    val toLegGroupId: String?,
    val transferCount: Int?,
    val durationLimit: Int?,
    val durationLimitType: Int?,
    val fareTransferType: Int?,
    val fareProductId: String?,
) {
    companion object {
        fun from(r: GtfsRecord) =
            FareTransferRuleRow(
                line = r.line,
                fromLegGroupId = r.string("from_leg_group_id"),
                toLegGroupId = r.string("to_leg_group_id"),
                transferCount = r.int("transfer_count"),
                durationLimit = r.int("duration_limit"),
                durationLimitType = r.int("duration_limit_type"),
                fareTransferType = r.int("fare_transfer_type"),
                fareProductId = r.string("fare_product_id"),
            )
    }
}

data class AreaRow(
    val line: Long,
    val areaId: String?,
    val areaName: String?,
) {
    companion object {
        fun from(r: GtfsRecord) =
            AreaRow(
                line = r.line,
                areaId = r.string("area_id"),
                areaName = r.string("area_name"),
            )
    }
}

data class StopAreaRow(
    val line: Long,
    val areaId: String?,
    val stopId: String?,
) {
    companion object {
        fun from(r: GtfsRecord) =
            StopAreaRow(
                line = r.line,
                areaId = r.string("area_id"),
                stopId = r.string("stop_id"),
            )
    }
}

data class NetworkRow(
    val line: Long,
    val networkId: String?,
    val networkName: String?,
) {
    companion object {
        fun from(r: GtfsRecord) =
            NetworkRow(
                line = r.line,
                networkId = r.string("network_id"),
                networkName = r.string("network_name"),
            )
    }
}

data class RouteNetworkRow(
    val line: Long,
    val networkId: String?,
    val routeId: String?,
) {
    companion object {
        fun from(r: GtfsRecord) =
            RouteNetworkRow(
                line = r.line,
                networkId = r.string("network_id"),
                routeId = r.string("route_id"),
            )
    }
}
