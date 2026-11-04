package eu.transittrack.gtfs.model

import org.jetbrains.exposed.v1.core.Coalesce
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.core.stringLiteral
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

/**
 * Typed row classes for the GTFS Fares v2 (and v1) files: fare_attributes, fare_rules, timeframes,
 * rider_categories, fare_media, fare_products, fare_leg_rules, fare_leg_join_rules,
 * fare_transfer_rules, areas, stop_areas, networks, route_networks.
 *
 * GTFS enum integers are stored as `SMALLINT` and read as Kotlin `Int?`.
 * `timeframe.start_time` / `end_time` are stored as seconds-of-day (`INT`).
 */
class FareAttribute(
    revisionId: Long,
    var fareId: String,
    var price: Double?,
    var currencyType: String?,
    var paymentMethod: Int?,
    var transfers: Int?,
    var agencyId: String?,
    var transferDuration: Int?,
) : RevisionScoped(revisionId)

class FareRule(
    revisionId: Long,
    var fareId: String,
    var routeId: String?,
    var originId: String?,
    var destinationId: String?,
    var containsId: String?,
) : RevisionScoped(revisionId)

class Timeframe(
    revisionId: Long,
    var timeframeGroupId: String,
    var startTime: Int?,
    var endTime: Int?,
    var serviceId: String,
) : RevisionScoped(revisionId)

class RiderCategory(
    revisionId: Long,
    var riderCategoryId: String,
    var riderCategoryName: String?,
    var isDefaultFareCategory: Int?,
    var eligibilityUrl: String?,
) : RevisionScoped(revisionId)

class FareMedia(
    revisionId: Long,
    var fareMediaId: String,
    var fareMediaName: String?,
    var fareMediaType: Int?,
) : RevisionScoped(revisionId)

class FareProduct(
    revisionId: Long,
    var fareProductId: String,
    var fareProductName: String?,
    var riderCategoryId: String?,
    var fareMediaId: String?,
    var amount: Double?,
    var currency: String?,
) : RevisionScoped(revisionId)

class FareLegRule(
    revisionId: Long,
    var legGroupId: String?,
    var networkId: String?,
    var fromAreaId: String?,
    var toAreaId: String?,
    var fromTimeframeGroupId: String?,
    var toTimeframeGroupId: String?,
    var fareProductId: String,
    var rulePriority: Int?,
) : RevisionScoped(revisionId)

class FareLegJoinRule(
    revisionId: Long,
    var fromNetworkId: String,
    var toNetworkId: String,
    var fromStopId: String?,
    var toStopId: String?,
) : RevisionScoped(revisionId)

class FareTransferRule(
    revisionId: Long,
    var fromLegGroupId: String?,
    var toLegGroupId: String?,
    var transferCount: Int?,
    var durationLimit: Int?,
    var durationLimitType: Int?,
    var fareTransferType: Int?,
    var fareProductId: String?,
) : RevisionScoped(revisionId)

class Area(
    revisionId: Long,
    var areaId: String,
    var areaName: String?,
) : RevisionScoped(revisionId)

class StopArea(
    revisionId: Long,
    var areaId: String,
    var stopId: String,
) : RevisionScoped(revisionId)

class Network(
    revisionId: Long,
    var networkId: String,
    var networkName: String?,
) : RevisionScoped(revisionId)

class RouteNetwork(
    revisionId: Long,
    var networkId: String,
    var routeId: String,
) : RevisionScoped(revisionId)

object FareAttributeTable : RevisionScopedTable<FareAttribute>("fare_attributes", FareAttribute::class.java, "fk_fare_attr_rev") {
    val fareId = varchar("fare_id", 255)
    val price = double("price").nullable()
    val currencyType = varchar("currency_type", 8).nullable()
    val paymentMethod = short("payment_method").nullable()
    val transfers = short("transfers").nullable()
    val agencyId = varchar("agency_id", 255).nullable()
    val transferDuration = integer("transfer_duration").nullable()

    init {
        uniqueIndex("uq_fare_attr_rev_id", revisionId, fareId)
    }

    override fun read(row: ResultRow) =
        FareAttribute(
            revisionId = row[revisionId],
            fareId = row[fareId],
            price = row[price],
            currencyType = row[currencyType],
            paymentMethod = row[paymentMethod]?.toInt(),
            transfers = row[transfers]?.toInt(),
            agencyId = row[agencyId],
            transferDuration = row[transferDuration],
        ).withId(row)

    override fun bind(
        s: UpdateBuilder<*>,
        row: FareAttribute,
    ) {
        s[revisionId] = row.revisionId
        s[fareId] = row.fareId
        s[price] = row.price
        s[currencyType] = row.currencyType
        s[paymentMethod] = row.paymentMethod?.toShort()
        s[transfers] = row.transfers?.toShort()
        s[agencyId] = row.agencyId
        s[transferDuration] = row.transferDuration
    }
}

object FareRuleTable : RevisionScopedTable<FareRule>("fare_rules", FareRule::class.java, "fk_fare_rule_rev") {
    val fareId = varchar("fare_id", 255)
    val routeId = varchar("route_id", 255).nullable()
    val originId = varchar("origin_id", 255).nullable()
    val destinationId = varchar("destination_id", 255).nullable()
    val containsId = varchar("contains_id", 255).nullable()

    init {
        index("ix_fare_rule_rev_fare", false, revisionId, fareId)
    }

    override fun read(row: ResultRow) =
        FareRule(
            revisionId = row[revisionId],
            fareId = row[fareId],
            routeId = row[routeId],
            originId = row[originId],
            destinationId = row[destinationId],
            containsId = row[containsId],
        ).withId(row)

    override fun bind(
        s: UpdateBuilder<*>,
        row: FareRule,
    ) {
        s[revisionId] = row.revisionId
        s[fareId] = row.fareId
        s[routeId] = row.routeId
        s[originId] = row.originId
        s[destinationId] = row.destinationId
        s[containsId] = row.containsId
    }
}

object TimeframeTable : RevisionScopedTable<Timeframe>("timeframes", Timeframe::class.java, "fk_timeframe_rev") {
    val timeframeGroupId = varchar("timeframe_group_id", 255)
    val startTime = integer("start_time").nullable()
    val endTime = integer("end_time").nullable()
    val serviceId = varchar("service_id", 255)

    init {
        uniqueIndex("uq_timeframe_rev_grp", revisionId, timeframeGroupId, startTime, endTime, serviceId)
    }

    override fun read(row: ResultRow) =
        Timeframe(
            revisionId = row[revisionId],
            timeframeGroupId = row[timeframeGroupId],
            startTime = row[startTime],
            endTime = row[endTime],
            serviceId = row[serviceId],
        ).withId(row)

    override fun bind(
        s: UpdateBuilder<*>,
        row: Timeframe,
    ) {
        s[revisionId] = row.revisionId
        s[timeframeGroupId] = row.timeframeGroupId
        s[startTime] = row.startTime
        s[endTime] = row.endTime
        s[serviceId] = row.serviceId
    }
}

object RiderCategoryTable : RevisionScopedTable<RiderCategory>("rider_categories", RiderCategory::class.java, "fk_rider_cat_rev") {
    val riderCategoryId = varchar("rider_category_id", 255)
    val riderCategoryName = varchar("rider_category_name", 255).nullable()
    val isDefaultFareCategory = short("is_default_fare_category").nullable()
    val eligibilityUrl = text("eligibility_url").nullable()

    init {
        uniqueIndex("uq_rider_cat_rev_id", revisionId, riderCategoryId)
    }

    override fun read(row: ResultRow) =
        RiderCategory(
            revisionId = row[revisionId],
            riderCategoryId = row[riderCategoryId],
            riderCategoryName = row[riderCategoryName],
            isDefaultFareCategory = row[isDefaultFareCategory]?.toInt(),
            eligibilityUrl = row[eligibilityUrl],
        ).withId(row)

    override fun bind(
        s: UpdateBuilder<*>,
        row: RiderCategory,
    ) {
        s[revisionId] = row.revisionId
        s[riderCategoryId] = row.riderCategoryId
        s[riderCategoryName] = row.riderCategoryName
        s[isDefaultFareCategory] = row.isDefaultFareCategory?.toShort()
        s[eligibilityUrl] = row.eligibilityUrl
    }
}

object FareMediaTable : RevisionScopedTable<FareMedia>("fare_media", FareMedia::class.java, "fk_fare_media_rev") {
    val fareMediaId = varchar("fare_media_id", 255)
    val fareMediaName = varchar("fare_media_name", 255).nullable()
    val fareMediaType = short("fare_media_type").nullable()

    init {
        uniqueIndex("uq_fare_media_rev_id", revisionId, fareMediaId)
    }

    override fun read(row: ResultRow) =
        FareMedia(
            revisionId = row[revisionId],
            fareMediaId = row[fareMediaId],
            fareMediaName = row[fareMediaName],
            fareMediaType = row[fareMediaType]?.toInt(),
        ).withId(row)

    override fun bind(
        s: UpdateBuilder<*>,
        row: FareMedia,
    ) {
        s[revisionId] = row.revisionId
        s[fareMediaId] = row.fareMediaId
        s[fareMediaName] = row.fareMediaName
        s[fareMediaType] = row.fareMediaType?.toShort()
    }
}

object FareProductTable : RevisionScopedTable<FareProduct>("fare_products", FareProduct::class.java, "fk_fare_prod_rev") {
    val fareProductId = varchar("fare_product_id", 255)
    val fareProductName = varchar("fare_product_name", 255).nullable()
    val riderCategoryId = varchar("rider_category_id", 255).nullable()
    val fareMediaId = varchar("fare_media_id", 255).nullable()
    val amount = double("amount").nullable()
    val currency = varchar("currency", 8).nullable()

    init {
        uniqueIndex(
            "uq_fare_product",
            revisionId,
            fareProductId,
            functions = listOf(Coalesce(riderCategoryId, stringLiteral("")), Coalesce(fareMediaId, stringLiteral(""))),
        )
    }

    override fun read(row: ResultRow) =
        FareProduct(
            revisionId = row[revisionId],
            fareProductId = row[fareProductId],
            fareProductName = row[fareProductName],
            riderCategoryId = row[riderCategoryId],
            fareMediaId = row[fareMediaId],
            amount = row[amount],
            currency = row[currency],
        ).withId(row)

    override fun bind(
        s: UpdateBuilder<*>,
        row: FareProduct,
    ) {
        s[revisionId] = row.revisionId
        s[fareProductId] = row.fareProductId
        s[fareProductName] = row.fareProductName
        s[riderCategoryId] = row.riderCategoryId
        s[fareMediaId] = row.fareMediaId
        s[amount] = row.amount
        s[currency] = row.currency
    }
}

object FareLegRuleTable : RevisionScopedTable<FareLegRule>("fare_leg_rules", FareLegRule::class.java, "fk_fare_leg_rule_rev") {
    val legGroupId = varchar("leg_group_id", 255).nullable()
    val networkId = varchar("network_id", 255).nullable()
    val fromAreaId = varchar("from_area_id", 255).nullable()
    val toAreaId = varchar("to_area_id", 255).nullable()
    val fromTimeframeGroupId = varchar("from_timeframe_group_id", 255).nullable()
    val toTimeframeGroupId = varchar("to_timeframe_group_id", 255).nullable()
    val fareProductId = varchar("fare_product_id", 255)
    val rulePriority = integer("rule_priority").nullable()

    init {
        index("ix_fare_leg_rule_rev", false, revisionId)
    }

    override fun read(row: ResultRow) =
        FareLegRule(
            revisionId = row[revisionId],
            legGroupId = row[legGroupId],
            networkId = row[networkId],
            fromAreaId = row[fromAreaId],
            toAreaId = row[toAreaId],
            fromTimeframeGroupId = row[fromTimeframeGroupId],
            toTimeframeGroupId = row[toTimeframeGroupId],
            fareProductId = row[fareProductId],
            rulePriority = row[rulePriority],
        ).withId(row)

    override fun bind(
        s: UpdateBuilder<*>,
        row: FareLegRule,
    ) {
        s[revisionId] = row.revisionId
        s[legGroupId] = row.legGroupId
        s[networkId] = row.networkId
        s[fromAreaId] = row.fromAreaId
        s[toAreaId] = row.toAreaId
        s[fromTimeframeGroupId] = row.fromTimeframeGroupId
        s[toTimeframeGroupId] = row.toTimeframeGroupId
        s[fareProductId] = row.fareProductId
        s[rulePriority] = row.rulePriority
    }
}

object FareLegJoinRuleTable :
    RevisionScopedTable<FareLegJoinRule>("fare_leg_join_rules", FareLegJoinRule::class.java, "fk_fare_leg_join_rev") {
    val fromNetworkId = varchar("from_network_id", 255)
    val toNetworkId = varchar("to_network_id", 255)
    val fromStopId = varchar("from_stop_id", 255).nullable()
    val toStopId = varchar("to_stop_id", 255).nullable()

    init {
        index("ix_fare_leg_join_rev", false, revisionId)
    }

    override fun read(row: ResultRow) =
        FareLegJoinRule(
            revisionId = row[revisionId],
            fromNetworkId = row[fromNetworkId],
            toNetworkId = row[toNetworkId],
            fromStopId = row[fromStopId],
            toStopId = row[toStopId],
        ).withId(row)

    override fun bind(
        s: UpdateBuilder<*>,
        row: FareLegJoinRule,
    ) {
        s[revisionId] = row.revisionId
        s[fromNetworkId] = row.fromNetworkId
        s[toNetworkId] = row.toNetworkId
        s[fromStopId] = row.fromStopId
        s[toStopId] = row.toStopId
    }
}

object FareTransferRuleTable :
    RevisionScopedTable<FareTransferRule>("fare_transfer_rules", FareTransferRule::class.java, "fk_fare_xfer_rule_rev") {
    val fromLegGroupId = varchar("from_leg_group_id", 255).nullable()
    val toLegGroupId = varchar("to_leg_group_id", 255).nullable()
    val transferCount = integer("transfer_count").nullable()
    val durationLimit = integer("duration_limit").nullable()
    val durationLimitType = short("duration_limit_type").nullable()
    val fareTransferType = short("fare_transfer_type").nullable()
    val fareProductId = varchar("fare_product_id", 255).nullable()

    init {
        index("ix_fare_xfer_rule_rev", false, revisionId)
    }

    override fun read(row: ResultRow) =
        FareTransferRule(
            revisionId = row[revisionId],
            fromLegGroupId = row[fromLegGroupId],
            toLegGroupId = row[toLegGroupId],
            transferCount = row[transferCount],
            durationLimit = row[durationLimit],
            durationLimitType = row[durationLimitType]?.toInt(),
            fareTransferType = row[fareTransferType]?.toInt(),
            fareProductId = row[fareProductId],
        ).withId(row)

    override fun bind(
        s: UpdateBuilder<*>,
        row: FareTransferRule,
    ) {
        s[revisionId] = row.revisionId
        s[fromLegGroupId] = row.fromLegGroupId
        s[toLegGroupId] = row.toLegGroupId
        s[transferCount] = row.transferCount
        s[durationLimit] = row.durationLimit
        s[durationLimitType] = row.durationLimitType?.toShort()
        s[fareTransferType] = row.fareTransferType?.toShort()
        s[fareProductId] = row.fareProductId
    }
}

object AreaTable : RevisionScopedTable<Area>("areas", Area::class.java, "fk_area_rev") {
    val areaId = varchar("area_id", 255)
    val areaName = varchar("area_name", 255).nullable()

    init {
        uniqueIndex("uq_area_rev_id", revisionId, areaId)
    }

    override fun read(row: ResultRow) = Area(revisionId = row[revisionId], areaId = row[areaId], areaName = row[areaName]).withId(row)

    override fun bind(
        s: UpdateBuilder<*>,
        row: Area,
    ) {
        s[revisionId] = row.revisionId
        s[areaId] = row.areaId
        s[areaName] = row.areaName
    }
}

object StopAreaTable : RevisionScopedTable<StopArea>("stop_areas", StopArea::class.java, "fk_stop_area_rev") {
    val areaId = varchar("area_id", 255)
    val stopId = varchar("stop_id", 255)

    init {
        uniqueIndex("uq_stop_area_rev", revisionId, areaId, stopId)
    }

    override fun read(row: ResultRow) = StopArea(revisionId = row[revisionId], areaId = row[areaId], stopId = row[stopId]).withId(row)

    override fun bind(
        s: UpdateBuilder<*>,
        row: StopArea,
    ) {
        s[revisionId] = row.revisionId
        s[areaId] = row.areaId
        s[stopId] = row.stopId
    }
}

object NetworkTable : RevisionScopedTable<Network>("networks", Network::class.java, "fk_network_rev") {
    val networkId = varchar("network_id", 255)
    val networkName = varchar("network_name", 255).nullable()

    init {
        uniqueIndex("uq_network_rev_id", revisionId, networkId)
    }

    override fun read(row: ResultRow) =
        Network(revisionId = row[revisionId], networkId = row[networkId], networkName = row[networkName]).withId(row)

    override fun bind(
        s: UpdateBuilder<*>,
        row: Network,
    ) {
        s[revisionId] = row.revisionId
        s[networkId] = row.networkId
        s[networkName] = row.networkName
    }
}

object RouteNetworkTable : RevisionScopedTable<RouteNetwork>("route_networks", RouteNetwork::class.java, "fk_route_network_rev") {
    val networkId = varchar("network_id", 255)
    val routeId = varchar("route_id", 255)

    init {
        uniqueIndex("uq_route_network_rev", revisionId, routeId)
    }

    override fun read(row: ResultRow) =
        RouteNetwork(revisionId = row[revisionId], networkId = row[networkId], routeId = row[routeId]).withId(row)

    override fun bind(
        s: UpdateBuilder<*>,
        row: RouteNetwork,
    ) {
        s[revisionId] = row.revisionId
        s[networkId] = row.networkId
        s[routeId] = row.routeId
    }
}

@Repository
@Transactional
class FareAttributeRepository : RevisionScopedRepository<FareAttribute>(FareAttributeTable)

@Repository
@Transactional
class FareRuleRepository : RevisionScopedRepository<FareRule>(FareRuleTable)

@Repository
@Transactional
class TimeframeRepository : RevisionScopedRepository<Timeframe>(TimeframeTable)

@Repository
@Transactional
class RiderCategoryRepository : RevisionScopedRepository<RiderCategory>(RiderCategoryTable)

@Repository
@Transactional
class FareMediaRepository : RevisionScopedRepository<FareMedia>(FareMediaTable)

@Repository
@Transactional
class FareProductRepository : RevisionScopedRepository<FareProduct>(FareProductTable)

@Repository
@Transactional
class FareLegRuleRepository : RevisionScopedRepository<FareLegRule>(FareLegRuleTable)

@Repository
@Transactional
class FareLegJoinRuleRepository : RevisionScopedRepository<FareLegJoinRule>(FareLegJoinRuleTable)

@Repository
@Transactional
class FareTransferRuleRepository : RevisionScopedRepository<FareTransferRule>(FareTransferRuleTable)

@Repository
@Transactional
class AreaRepository : RevisionScopedRepository<Area>(AreaTable)

@Repository
@Transactional
class StopAreaRepository : RevisionScopedRepository<StopArea>(StopAreaTable)

@Repository
@Transactional
class NetworkRepository : RevisionScopedRepository<Network>(NetworkTable)

@Repository
@Transactional
class RouteNetworkRepository : RevisionScopedRepository<RouteNetwork>(RouteNetworkTable)
