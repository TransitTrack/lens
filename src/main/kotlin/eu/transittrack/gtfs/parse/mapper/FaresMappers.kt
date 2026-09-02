package eu.transittrack.gtfs.parse.mapper

import eu.transittrack.gtfs.model.*
import org.mobilitydata.gtfsvalidator.table.GtfsArea
import org.mobilitydata.gtfsvalidator.table.GtfsFareAttribute
import org.mobilitydata.gtfsvalidator.table.GtfsFareLegJoinRule
import org.mobilitydata.gtfsvalidator.table.GtfsFareLegRule
import org.mobilitydata.gtfsvalidator.table.GtfsFareMedia
import org.mobilitydata.gtfsvalidator.table.GtfsFareProduct
import org.mobilitydata.gtfsvalidator.table.GtfsFareRule
import org.mobilitydata.gtfsvalidator.table.GtfsFareTransferRule
import org.mobilitydata.gtfsvalidator.table.GtfsNetwork
import org.mobilitydata.gtfsvalidator.table.GtfsRiderCategories
import org.mobilitydata.gtfsvalidator.table.GtfsRouteNetwork
import org.mobilitydata.gtfsvalidator.table.GtfsStopArea
import org.mobilitydata.gtfsvalidator.table.GtfsTimeframe

fun mapFareAttribute(rev: Long, r: GtfsFareAttribute) = FareAttribute(
    revisionId = rev,
    fareId = r.fareId().takeIf { r.hasFareId() } ?: error("fare_attributes.txt row missing fare_id"),
    price = r.price().takeIf { r.hasPrice() }?.toDouble(),
    currencyType = r.currencyType().takeIf { r.hasCurrencyType() }.codeOrNull(),
    paymentMethod = r.paymentMethodValue().takeIf { r.hasPaymentMethod() },
    transfers = r.transfersValue().takeIf { r.hasTransfers() },
    agencyId = r.agencyId().takeIf { r.hasAgencyId() },
    transferDuration = r.transferDuration().takeIf { r.hasTransferDuration() },
)

fun mapFareRule(rev: Long, r: GtfsFareRule) = FareRule(
    revisionId = rev,
    fareId = r.fareId().takeIf { r.hasFareId() } ?: error("fare_rules.txt row missing fare_id"),
    routeId = r.routeId().takeIf { r.hasRouteId() },
    originId = r.originId().takeIf { r.hasOriginId() },
    destinationId = r.destinationId().takeIf { r.hasDestinationId() },
    containsId = r.containsId().takeIf { r.hasContainsId() },
)

fun mapTimeframe(rev: Long, r: GtfsTimeframe) = Timeframe(
    revisionId = rev,
    timeframeGroupId = r.timeframeGroupId().takeIf { r.hasTimeframeGroupId() }
        ?: error("timeframes.txt row missing timeframe_group_id"),
    startTime = r.startTime().takeIf { r.hasStartTime() }.toSeconds(),
    endTime = r.endTime().takeIf { r.hasEndTime() }.toSeconds(),
    serviceId = r.serviceId().takeIf { r.hasServiceId() } ?: error("timeframes.txt row missing service_id"),
)

fun mapRiderCategory(rev: Long, r: GtfsRiderCategories) = RiderCategory(
    revisionId = rev,
    riderCategoryId = r.riderCategoryId().takeIf { r.hasRiderCategoryId() }
        ?: error("rider_categories.txt row missing rider_category_id"),
    riderCategoryName = r.riderCategoryName().takeIf { r.hasRiderCategoryName() },
    isDefaultFareCategory = r.isDefaultFareCategoryValue().takeIf { r.hasIsDefaultFareCategory() },
    eligibilityUrl = r.eligibilityUrl().takeIf { r.hasEligibilityUrl() },
)

fun mapFareMedia(rev: Long, r: GtfsFareMedia) = FareMedia(
    revisionId = rev,
    fareMediaId = r.fareMediaId().takeIf { r.hasFareMediaId() } ?: error("fare_media.txt row missing fare_media_id"),
    fareMediaName = r.fareMediaName().takeIf { r.hasFareMediaName() },
    fareMediaType = r.fareMediaTypeValue().takeIf { r.hasFareMediaType() },
)

fun mapFareProduct(rev: Long, r: GtfsFareProduct) = FareProduct(
    revisionId = rev,
    fareProductId = r.fareProductId().takeIf { r.hasFareProductId() }
        ?: error("fare_products.txt row missing fare_product_id"),
    fareProductName = r.fareProductName().takeIf { r.hasFareProductName() },
    riderCategoryId = r.riderCategoryId().takeIf { r.hasRiderCategoryId() },
    fareMediaId = r.fareMediaId().takeIf { r.hasFareMediaId() },
    amount = r.amount().takeIf { r.hasAmount() }?.toDouble(),
    currency = r.currency().takeIf { r.hasCurrency() }.codeOrNull(),
)

fun mapFareLegRule(rev: Long, r: GtfsFareLegRule) = FareLegRule(
    revisionId = rev,
    legGroupId = r.legGroupId().takeIf { r.hasLegGroupId() },
    networkId = r.networkId().takeIf { r.hasNetworkId() },
    fromAreaId = r.fromAreaId().takeIf { r.hasFromAreaId() },
    toAreaId = r.toAreaId().takeIf { r.hasToAreaId() },
    fromTimeframeGroupId = r.fromTimeframeGroupId().takeIf { r.hasFromTimeframeGroupId() },
    toTimeframeGroupId = r.toTimeframeGroupId().takeIf { r.hasToTimeframeGroupId() },
    fareProductId = r.fareProductId().takeIf { r.hasFareProductId() }
        ?: error("fare_leg_rules.txt row missing fare_product_id"),
    rulePriority = r.rulePriority().takeIf { r.hasRulePriority() },
)

fun mapFareLegJoinRule(rev: Long, r: GtfsFareLegJoinRule) = FareLegJoinRule(
    revisionId = rev,
    fromNetworkId = r.fromNetworkId().takeIf { r.hasFromNetworkId() }
        ?: error("fare_leg_join_rules.txt row missing from_network_id"),
    toNetworkId = r.toNetworkId().takeIf { r.hasToNetworkId() }
        ?: error("fare_leg_join_rules.txt row missing to_network_id"),
    fromStopId = r.fromStopId().takeIf { r.hasFromStopId() },
    toStopId = r.toStopId().takeIf { r.hasToStopId() },
)

fun mapFareTransferRule(rev: Long, r: GtfsFareTransferRule) = FareTransferRule(
    revisionId = rev,
    fromLegGroupId = r.fromLegGroupId().takeIf { r.hasFromLegGroupId() },
    toLegGroupId = r.toLegGroupId().takeIf { r.hasToLegGroupId() },
    transferCount = r.transferCount().takeIf { r.hasTransferCount() },
    durationLimit = r.durationLimit().takeIf { r.hasDurationLimit() },
    durationLimitType = r.durationLimitTypeValue().takeIf { r.hasDurationLimitType() },
    fareTransferType = r.fareTransferTypeValue().takeIf { r.hasFareTransferType() },
    fareProductId = r.fareProductId().takeIf { r.hasFareProductId() },
)

fun mapArea(rev: Long, r: GtfsArea) = Area(
    revisionId = rev,
    areaId = r.areaId().takeIf { r.hasAreaId() } ?: error("areas.txt row missing area_id"),
    areaName = r.areaName().takeIf { r.hasAreaName() },
)

fun mapStopArea(rev: Long, r: GtfsStopArea) = StopArea(
    revisionId = rev,
    areaId = r.areaId().takeIf { r.hasAreaId() } ?: error("stop_areas.txt row missing area_id"),
    stopId = r.stopId().takeIf { r.hasStopId() } ?: error("stop_areas.txt row missing stop_id"),
)

fun mapNetwork(rev: Long, r: GtfsNetwork) = Network(
    revisionId = rev,
    networkId = r.networkId().takeIf { r.hasNetworkId() } ?: error("networks.txt row missing network_id"),
    networkName = r.networkName().takeIf { r.hasNetworkName() },
)

fun mapRouteNetwork(rev: Long, r: GtfsRouteNetwork) = RouteNetwork(
    revisionId = rev,
    networkId = r.networkId().takeIf { r.hasNetworkId() } ?: error("route_networks.txt row missing network_id"),
    routeId = r.routeId().takeIf { r.hasRouteId() } ?: error("route_networks.txt row missing route_id"),
)
