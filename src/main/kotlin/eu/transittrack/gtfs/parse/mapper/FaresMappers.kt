package eu.transittrack.gtfs.parse.mapper

import eu.transittrack.gtfs.model.*
import eu.transittrack.gtfs.parse.GtfsRow

fun mapFareAttribute(rev: Long, r: GtfsRow) = GtfsFareAttribute(
    revisionId = rev,
    fareId = r.str("fare_id") ?: error("fare_attributes.txt row missing fare_id"),
    price = r.double("price"),
    currencyType = r.str("currency_type"),
    paymentMethod = r.int("payment_method"),
    transfers = r.int("transfers"),
    agencyId = r.str("agency_id"),
    transferDuration = r.int("transfer_duration"),
)

fun mapFareRule(rev: Long, r: GtfsRow) = GtfsFareRule(
    revisionId = rev,
    fareId = r.str("fare_id") ?: error("fare_rules.txt row missing fare_id"),
    routeId = r.str("route_id"),
    originId = r.str("origin_id"),
    destinationId = r.str("destination_id"),
    containsId = r.str("contains_id"),
)

fun mapTimeframe(rev: Long, r: GtfsRow) = GtfsTimeframe(
    revisionId = rev,
    timeframeGroupId = r.str("timeframe_group_id") ?: error("timeframes.txt row missing timeframe_group_id"),
    startTime = r.seconds("start_time"),
    endTime = r.seconds("end_time"),
    serviceId = r.str("service_id") ?: error("timeframes.txt row missing service_id"),
)

fun mapRiderCategory(rev: Long, r: GtfsRow) = GtfsRiderCategory(
    revisionId = rev,
    riderCategoryId = r.str("rider_category_id") ?: error("rider_categories.txt row missing rider_category_id"),
    riderCategoryName = r.str("rider_category_name"),
    isDefaultFareCategory = r.int("is_default_fare_category"),
    eligibilityUrl = r.str("eligibility_url"),
)

fun mapFareMedia(rev: Long, r: GtfsRow) = GtfsFareMedia(
    revisionId = rev,
    fareMediaId = r.str("fare_media_id") ?: error("fare_media.txt row missing fare_media_id"),
    fareMediaName = r.str("fare_media_name"),
    fareMediaType = r.int("fare_media_type"),
)

fun mapFareProduct(rev: Long, r: GtfsRow) = GtfsFareProduct(
    revisionId = rev,
    fareProductId = r.str("fare_product_id") ?: error("fare_products.txt row missing fare_product_id"),
    fareProductName = r.str("fare_product_name"),
    riderCategoryId = r.str("rider_category_id"),
    fareMediaId = r.str("fare_media_id"),
    amount = r.double("amount"),
    currency = r.str("currency"),
)

fun mapFareLegRule(rev: Long, r: GtfsRow) = GtfsFareLegRule(
    revisionId = rev,
    legGroupId = r.str("leg_group_id"),
    networkId = r.str("network_id"),
    fromAreaId = r.str("from_area_id"),
    toAreaId = r.str("to_area_id"),
    fromTimeframeGroupId = r.str("from_timeframe_group_id"),
    toTimeframeGroupId = r.str("to_timeframe_group_id"),
    fareProductId = r.str("fare_product_id") ?: error("fare_leg_rules.txt row missing fare_product_id"),
    rulePriority = r.int("rule_priority"),
)

fun mapFareLegJoinRule(rev: Long, r: GtfsRow) = GtfsFareLegJoinRule(
    revisionId = rev,
    fromNetworkId = r.str("from_network_id") ?: error("fare_leg_join_rules.txt row missing from_network_id"),
    toNetworkId = r.str("to_network_id") ?: error("fare_leg_join_rules.txt row missing to_network_id"),
    fromStopId = r.str("from_stop_id"),
    toStopId = r.str("to_stop_id"),
)

fun mapFareTransferRule(rev: Long, r: GtfsRow) = GtfsFareTransferRule(
    revisionId = rev,
    fromLegGroupId = r.str("from_leg_group_id"),
    toLegGroupId = r.str("to_leg_group_id"),
    transferCount = r.int("transfer_count"),
    durationLimit = r.int("duration_limit"),
    durationLimitType = r.int("duration_limit_type"),
    fareTransferType = r.int("fare_transfer_type"),
    fareProductId = r.str("fare_product_id"),
)

fun mapArea(rev: Long, r: GtfsRow) = GtfsArea(
    revisionId = rev,
    areaId = r.str("area_id") ?: error("areas.txt row missing area_id"),
    areaName = r.str("area_name"),
)

fun mapStopArea(rev: Long, r: GtfsRow) = GtfsStopArea(
    revisionId = rev,
    areaId = r.str("area_id") ?: error("stop_areas.txt row missing area_id"),
    stopId = r.str("stop_id") ?: error("stop_areas.txt row missing stop_id"),
)

fun mapNetwork(rev: Long, r: GtfsRow) = GtfsNetwork(
    revisionId = rev,
    networkId = r.str("network_id") ?: error("networks.txt row missing network_id"),
    networkName = r.str("network_name"),
)

fun mapRouteNetwork(rev: Long, r: GtfsRow) = GtfsRouteNetwork(
    revisionId = rev,
    networkId = r.str("network_id") ?: error("route_networks.txt row missing network_id"),
    routeId = r.str("route_id") ?: error("route_networks.txt row missing route_id"),
)
