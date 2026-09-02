package eu.transittrack.gtfs.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

/**
 * Typed JPA entities for the GTFS Fares v2 (and v1) files: fare_attributes, fare_rules, timeframes,
 * rider_categories, fare_media, fare_products, fare_leg_rules, fare_leg_join_rules,
 * fare_transfer_rules, areas, stop_areas, networks, route_networks.
 *
 * GTFS enum integers are stored as `SMALLINT`; the matching Kotlin `Int?` fields carry
 * `@JdbcTypeCode(SqlTypes.SMALLINT)` so Hibernate `ddl-auto: validate` sees matching JDBC type
 * codes. `timeframe.start_time` / `end_time` are stored as seconds-of-day (`INT`).
 */
@Entity
@Table(name = "fare_attributes")
class FareAttribute(
    revisionId: Long,
    @Column(name = "fare_id", nullable = false) var fareId: String,
    var price: Double?,
    var currencyType: String?,
    @JdbcTypeCode(SqlTypes.SMALLINT) var paymentMethod: Int?,
    @JdbcTypeCode(SqlTypes.SMALLINT) var transfers: Int?,
    var agencyId: String?,
    var transferDuration: Int?,
) : RevisionScoped(revisionId)

@Entity
@Table(name = "fare_rules")
class FareRule(
    revisionId: Long,
    @Column(name = "fare_id", nullable = false) var fareId: String,
    var routeId: String?,
    var originId: String?,
    var destinationId: String?,
    var containsId: String?,
) : RevisionScoped(revisionId)

@Entity
@Table(name = "timeframes")
class Timeframe(
    revisionId: Long,
    @Column(name = "timeframe_group_id", nullable = false) var timeframeGroupId: String,
    var startTime: Int?,
    var endTime: Int?,
    @Column(name = "service_id", nullable = false) var serviceId: String,
) : RevisionScoped(revisionId)

@Entity
@Table(name = "rider_categories")
class RiderCategory(
    revisionId: Long,
    @Column(name = "rider_category_id", nullable = false) var riderCategoryId: String,
    var riderCategoryName: String?,
    @JdbcTypeCode(SqlTypes.SMALLINT) @Column(name = "is_default_fare_category") var isDefaultFareCategory: Int?,
    var eligibilityUrl: String?,
) : RevisionScoped(revisionId)

@Entity
@Table(name = "fare_media")
class FareMedia(
    revisionId: Long,
    @Column(name = "fare_media_id", nullable = false) var fareMediaId: String,
    var fareMediaName: String?,
    @JdbcTypeCode(SqlTypes.SMALLINT) var fareMediaType: Int?,
) : RevisionScoped(revisionId)

@Entity
@Table(name = "fare_products")
class FareProduct(
    revisionId: Long,
    @Column(name = "fare_product_id", nullable = false) var fareProductId: String,
    var fareProductName: String?,
    var riderCategoryId: String?,
    var fareMediaId: String?,
    var amount: Double?,
    var currency: String?,
) : RevisionScoped(revisionId)

@Entity
@Table(name = "fare_leg_rules")
class FareLegRule(
    revisionId: Long,
    var legGroupId: String?,
    var networkId: String?,
    var fromAreaId: String?,
    var toAreaId: String?,
    var fromTimeframeGroupId: String?,
    var toTimeframeGroupId: String?,
    @Column(name = "fare_product_id", nullable = false) var fareProductId: String,
    var rulePriority: Int?,
) : RevisionScoped(revisionId)

@Entity
@Table(name = "fare_leg_join_rules")
class FareLegJoinRule(
    revisionId: Long,
    @Column(name = "from_network_id", nullable = false) var fromNetworkId: String,
    @Column(name = "to_network_id", nullable = false) var toNetworkId: String,
    var fromStopId: String?,
    var toStopId: String?,
) : RevisionScoped(revisionId)

@Entity
@Table(name = "fare_transfer_rules")
class FareTransferRule(
    revisionId: Long,
    var fromLegGroupId: String?,
    var toLegGroupId: String?,
    var transferCount: Int?,
    var durationLimit: Int?,
    @JdbcTypeCode(SqlTypes.SMALLINT) var durationLimitType: Int?,
    @JdbcTypeCode(SqlTypes.SMALLINT) var fareTransferType: Int?,
    var fareProductId: String?,
) : RevisionScoped(revisionId)

@Entity
@Table(name = "areas")
class Area(
    revisionId: Long,
    @Column(name = "area_id", nullable = false) var areaId: String,
    var areaName: String?,
) : RevisionScoped(revisionId)

@Entity
@Table(name = "stop_areas")
class StopArea(
    revisionId: Long,
    @Column(name = "area_id", nullable = false) var areaId: String,
    @Column(name = "stop_id", nullable = false) var stopId: String,
) : RevisionScoped(revisionId)

@Entity
@Table(name = "networks")
class Network(
    revisionId: Long,
    @Column(name = "network_id", nullable = false) var networkId: String,
    var networkName: String?,
) : RevisionScoped(revisionId)

@Entity
@Table(name = "route_networks")
class RouteNetwork(
    revisionId: Long,
    @Column(name = "network_id", nullable = false) var networkId: String,
    @Column(name = "route_id", nullable = false) var routeId: String,
) : RevisionScoped(revisionId)

interface FareAttributeRepository : RevisionScopedRepository<FareAttribute, Long>

interface FareRuleRepository : RevisionScopedRepository<FareRule, Long>

interface TimeframeRepository : RevisionScopedRepository<Timeframe, Long>

interface RiderCategoryRepository : RevisionScopedRepository<RiderCategory, Long>

interface FareMediaRepository : RevisionScopedRepository<FareMedia, Long>

interface FareProductRepository : RevisionScopedRepository<FareProduct, Long>

interface FareLegRuleRepository : RevisionScopedRepository<FareLegRule, Long>

interface FareLegJoinRuleRepository : RevisionScopedRepository<FareLegJoinRule, Long>

interface FareTransferRuleRepository : RevisionScopedRepository<FareTransferRule, Long>

interface AreaRepository : RevisionScopedRepository<Area, Long>

interface StopAreaRepository : RevisionScopedRepository<StopArea, Long>

interface NetworkRepository : RevisionScopedRepository<Network, Long>

interface RouteNetworkRepository : RevisionScopedRepository<RouteNetwork, Long>
