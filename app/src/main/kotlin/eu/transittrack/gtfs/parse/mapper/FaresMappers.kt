package eu.transittrack.gtfs.parse.mapper

import eu.transittrack.gtfs.model.Area
import eu.transittrack.gtfs.model.FareAttribute
import eu.transittrack.gtfs.model.FareLegJoinRule
import eu.transittrack.gtfs.model.FareLegRule
import eu.transittrack.gtfs.model.FareMedia
import eu.transittrack.gtfs.model.FareProduct
import eu.transittrack.gtfs.model.FareRule
import eu.transittrack.gtfs.model.FareTransferRule
import eu.transittrack.gtfs.model.Network
import eu.transittrack.gtfs.model.RiderCategory
import eu.transittrack.gtfs.model.RouteNetwork
import eu.transittrack.gtfs.model.StopArea
import eu.transittrack.gtfs.model.Timeframe
import eu.transittrack.gtfs.parse.MissingRequiredFieldException
import eu.transittrack.gtfs.parse.row.AreaRow
import eu.transittrack.gtfs.parse.row.FareAttributeRow
import eu.transittrack.gtfs.parse.row.FareLegJoinRuleRow
import eu.transittrack.gtfs.parse.row.FareLegRuleRow
import eu.transittrack.gtfs.parse.row.FareMediaRow
import eu.transittrack.gtfs.parse.row.FareProductRow
import eu.transittrack.gtfs.parse.row.FareRuleRow
import eu.transittrack.gtfs.parse.row.FareTransferRuleRow
import eu.transittrack.gtfs.parse.row.NetworkRow
import eu.transittrack.gtfs.parse.row.RiderCategoryRow
import eu.transittrack.gtfs.parse.row.RouteNetworkRow
import eu.transittrack.gtfs.parse.row.StopAreaRow
import eu.transittrack.gtfs.parse.row.TimeframeRow

fun mapFareAttribute(
    rev: Long,
    r: FareAttributeRow,
) = FareAttribute(
    revisionId = rev,
    fareId = r.fareId ?: throw MissingRequiredFieldException("fare_attributes.txt", r.line, "fare_id"),
    price = r.price,
    currencyType = r.currencyType,
    paymentMethod = r.paymentMethod,
    transfers = r.transfers,
    agencyId = r.agencyId,
    transferDuration = r.transferDuration,
)

fun mapFareRule(
    rev: Long,
    r: FareRuleRow,
) = FareRule(
    revisionId = rev,
    fareId = r.fareId ?: throw MissingRequiredFieldException("fare_rules.txt", r.line, "fare_id"),
    routeId = r.routeId,
    originId = r.originId,
    destinationId = r.destinationId,
    containsId = r.containsId,
)

fun mapTimeframe(
    rev: Long,
    r: TimeframeRow,
) = Timeframe(
    revisionId = rev,
    timeframeGroupId = r.timeframeGroupId
        ?: throw MissingRequiredFieldException("timeframes.txt", r.line, "timeframe_group_id"),
    startTime = r.startTime,
    endTime = r.endTime,
    serviceId = r.serviceId ?: throw MissingRequiredFieldException("timeframes.txt", r.line, "service_id"),
)

fun mapRiderCategory(
    rev: Long,
    r: RiderCategoryRow,
) = RiderCategory(
    revisionId = rev,
    riderCategoryId = r.riderCategoryId ?: throw MissingRequiredFieldException("rider_categories.txt", r.line, "rider_category_id"),
    riderCategoryName = r.riderCategoryName,
    isDefaultFareCategory = r.isDefaultFareCategory,
    eligibilityUrl = r.eligibilityUrl,
)

fun mapFareMedia(
    rev: Long,
    r: FareMediaRow,
) = FareMedia(
    revisionId = rev,
    fareMediaId = r.fareMediaId ?: throw MissingRequiredFieldException("fare_media.txt", r.line, "fare_media_id"),
    fareMediaName = r.fareMediaName,
    fareMediaType = r.fareMediaType,
)

fun mapFareProduct(
    rev: Long,
    r: FareProductRow,
) = FareProduct(
    revisionId = rev,
    fareProductId = r.fareProductId ?: throw MissingRequiredFieldException("fare_products.txt", r.line, "fare_product_id"),
    fareProductName = r.fareProductName,
    riderCategoryId = r.riderCategoryId,
    fareMediaId = r.fareMediaId,
    amount = r.amount,
    currency = r.currency,
)

fun mapFareLegRule(
    rev: Long,
    r: FareLegRuleRow,
) = FareLegRule(
    revisionId = rev,
    legGroupId = r.legGroupId,
    networkId = r.networkId,
    fromAreaId = r.fromAreaId,
    toAreaId = r.toAreaId,
    fromTimeframeGroupId = r.fromTimeframeGroupId,
    toTimeframeGroupId = r.toTimeframeGroupId,
    fareProductId = r.fareProductId ?: throw MissingRequiredFieldException("fare_leg_rules.txt", r.line, "fare_product_id"),
    rulePriority = r.rulePriority,
)

fun mapFareLegJoinRule(
    rev: Long,
    r: FareLegJoinRuleRow,
) = FareLegJoinRule(
    revisionId = rev,
    fromNetworkId = r.fromNetworkId
        ?: throw MissingRequiredFieldException("fare_leg_join_rules.txt", r.line, "from_network_id"),
    toNetworkId = r.toNetworkId ?: throw MissingRequiredFieldException("fare_leg_join_rules.txt", r.line, "to_network_id"),
    fromStopId = r.fromStopId,
    toStopId = r.toStopId,
)

fun mapFareTransferRule(
    rev: Long,
    r: FareTransferRuleRow,
) = FareTransferRule(
    revisionId = rev,
    fromLegGroupId = r.fromLegGroupId,
    toLegGroupId = r.toLegGroupId,
    transferCount = r.transferCount,
    durationLimit = r.durationLimit,
    durationLimitType = r.durationLimitType,
    fareTransferType = r.fareTransferType,
    fareProductId = r.fareProductId,
)

fun mapArea(
    rev: Long,
    r: AreaRow,
) = Area(
    revisionId = rev,
    areaId = r.areaId ?: throw MissingRequiredFieldException("areas.txt", r.line, "area_id"),
    areaName = r.areaName,
)

fun mapStopArea(
    rev: Long,
    r: StopAreaRow,
) = StopArea(
    revisionId = rev,
    areaId = r.areaId ?: throw MissingRequiredFieldException("stop_areas.txt", r.line, "area_id"),
    stopId = r.stopId ?: throw MissingRequiredFieldException("stop_areas.txt", r.line, "stop_id"),
)

fun mapNetwork(
    rev: Long,
    r: NetworkRow,
) = Network(
    revisionId = rev,
    networkId = r.networkId ?: throw MissingRequiredFieldException("networks.txt", r.line, "network_id"),
    networkName = r.networkName,
)

fun mapRouteNetwork(
    rev: Long,
    r: RouteNetworkRow,
) = RouteNetwork(
    revisionId = rev,
    networkId = r.networkId ?: throw MissingRequiredFieldException("route_networks.txt", r.line, "network_id"),
    routeId = r.routeId ?: throw MissingRequiredFieldException("route_networks.txt", r.line, "route_id"),
)
