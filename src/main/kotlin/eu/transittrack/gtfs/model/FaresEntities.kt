package eu.transittrack.gtfs.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import org.springframework.data.jpa.repository.JpaRepository

/**
 * Typed JPA entities for the GTFS Fares v2 (and v1) files: fare_attributes,
 * fare_rules, timeframes, rider_categories, fare_media, fare_products,
 * fare_leg_rules, fare_leg_join_rules, fare_transfer_rules, areas, stop_areas,
 * networks, route_networks.
 *
 * GTFS enum integers are stored as `SMALLINT`; the matching Kotlin `Int?` fields
 * carry `@JdbcTypeCode(SqlTypes.SMALLINT)` so Hibernate `ddl-auto: validate`
 * sees matching JDBC type codes. `timeframe.start_time` / `end_time` are stored
 * as seconds-of-day (`INT`).
 */

@Entity
@Table(name = "gtfs_fare_attribute")
class GtfsFareAttribute(
    @Column(name = "revision_id", nullable = false) var revisionId: Long,
    @Column(name = "fare_id", nullable = false) var fareId: String,
    var price: Double?,
    var currencyType: String?,
    @JdbcTypeCode(SqlTypes.SMALLINT) var paymentMethod: Int?,
    @JdbcTypeCode(SqlTypes.SMALLINT) var transfers: Int?,
    var agencyId: String?,
    var transferDuration: Int?,
) : RevisionScoped()

@Entity
@Table(name = "gtfs_fare_rule")
class GtfsFareRule(
    @Column(name = "revision_id", nullable = false) var revisionId: Long,
    @Column(name = "fare_id", nullable = false) var fareId: String,
    var routeId: String?,
    var originId: String?,
    var destinationId: String?,
    var containsId: String?,
) : RevisionScoped()

@Entity
@Table(name = "gtfs_timeframe")
class GtfsTimeframe(
    @Column(name = "revision_id", nullable = false) var revisionId: Long,
    @Column(name = "timeframe_group_id", nullable = false) var timeframeGroupId: String,
    var startTime: Int?,
    var endTime: Int?,
    @Column(name = "service_id", nullable = false) var serviceId: String,
) : RevisionScoped()

@Entity
@Table(name = "gtfs_rider_category")
class GtfsRiderCategory(
    @Column(name = "revision_id", nullable = false) var revisionId: Long,
    @Column(name = "rider_category_id", nullable = false) var riderCategoryId: String,
    var riderCategoryName: String?,
    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "is_default_fare_category")
    var isDefaultFareCategory: Int?,
    var eligibilityUrl: String?,
) : RevisionScoped()

@Entity
@Table(name = "gtfs_fare_media")
class GtfsFareMedia(
    @Column(name = "revision_id", nullable = false) var revisionId: Long,
    @Column(name = "fare_media_id", nullable = false) var fareMediaId: String,
    var fareMediaName: String?,
    @JdbcTypeCode(SqlTypes.SMALLINT) var fareMediaType: Int?,
) : RevisionScoped()

@Entity
@Table(name = "gtfs_fare_product")
class GtfsFareProduct(
    @Column(name = "revision_id", nullable = false) var revisionId: Long,
    @Column(name = "fare_product_id", nullable = false) var fareProductId: String,
    var fareProductName: String?,
    var riderCategoryId: String?,
    var fareMediaId: String?,
    var amount: Double?,
    var currency: String?,
) : RevisionScoped()

@Entity
@Table(name = "gtfs_fare_leg_rule")
class GtfsFareLegRule(
    @Column(name = "revision_id", nullable = false) var revisionId: Long,
    var legGroupId: String?,
    var networkId: String?,
    var fromAreaId: String?,
    var toAreaId: String?,
    var fromTimeframeGroupId: String?,
    var toTimeframeGroupId: String?,
    @Column(name = "fare_product_id", nullable = false) var fareProductId: String,
    var rulePriority: Int?,
) : RevisionScoped()

@Entity
@Table(name = "gtfs_fare_leg_join_rule")
class GtfsFareLegJoinRule(
    @Column(name = "revision_id", nullable = false) var revisionId: Long,
    @Column(name = "from_network_id", nullable = false) var fromNetworkId: String,
    @Column(name = "to_network_id", nullable = false) var toNetworkId: String,
    var fromStopId: String?,
    var toStopId: String?,
) : RevisionScoped()

@Entity
@Table(name = "gtfs_fare_transfer_rule")
class GtfsFareTransferRule(
    @Column(name = "revision_id", nullable = false) var revisionId: Long,
    var fromLegGroupId: String?,
    var toLegGroupId: String?,
    var transferCount: Int?,
    var durationLimit: Int?,
    @JdbcTypeCode(SqlTypes.SMALLINT) var durationLimitType: Int?,
    @JdbcTypeCode(SqlTypes.SMALLINT) var fareTransferType: Int?,
    var fareProductId: String?,
) : RevisionScoped()

@Entity
@Table(name = "gtfs_area")
class GtfsArea(
    @Column(name = "revision_id", nullable = false) var revisionId: Long,
    @Column(name = "area_id", nullable = false) var areaId: String,
    var areaName: String?,
) : RevisionScoped()

@Entity
@Table(name = "gtfs_stop_area")
class GtfsStopArea(
    @Column(name = "revision_id", nullable = false) var revisionId: Long,
    @Column(name = "area_id", nullable = false) var areaId: String,
    @Column(name = "stop_id", nullable = false) var stopId: String,
) : RevisionScoped()

@Entity
@Table(name = "gtfs_network")
class GtfsNetwork(
    @Column(name = "revision_id", nullable = false) var revisionId: Long,
    @Column(name = "network_id", nullable = false) var networkId: String,
    var networkName: String?,
) : RevisionScoped()

@Entity
@Table(name = "gtfs_route_network")
class GtfsRouteNetwork(
    @Column(name = "revision_id", nullable = false) var revisionId: Long,
    @Column(name = "network_id", nullable = false) var networkId: String,
    @Column(name = "route_id", nullable = false) var routeId: String,
) : RevisionScoped()

interface GtfsFareAttributeRepository : JpaRepository<GtfsFareAttribute, Long> {
    fun findByRevisionId(revisionId: Long): List<GtfsFareAttribute>
}

interface GtfsFareRuleRepository : JpaRepository<GtfsFareRule, Long> {
    fun findByRevisionId(revisionId: Long): List<GtfsFareRule>
}

interface GtfsTimeframeRepository : JpaRepository<GtfsTimeframe, Long> {
    fun findByRevisionId(revisionId: Long): List<GtfsTimeframe>
}

interface GtfsRiderCategoryRepository : JpaRepository<GtfsRiderCategory, Long> {
    fun findByRevisionId(revisionId: Long): List<GtfsRiderCategory>
}

interface GtfsFareMediaRepository : JpaRepository<GtfsFareMedia, Long> {
    fun findByRevisionId(revisionId: Long): List<GtfsFareMedia>
}

interface GtfsFareProductRepository : JpaRepository<GtfsFareProduct, Long> {
    fun findByRevisionId(revisionId: Long): List<GtfsFareProduct>
}

interface GtfsFareLegRuleRepository : JpaRepository<GtfsFareLegRule, Long> {
    fun findByRevisionId(revisionId: Long): List<GtfsFareLegRule>
}

interface GtfsFareLegJoinRuleRepository : JpaRepository<GtfsFareLegJoinRule, Long> {
    fun findByRevisionId(revisionId: Long): List<GtfsFareLegJoinRule>
}

interface GtfsFareTransferRuleRepository : JpaRepository<GtfsFareTransferRule, Long> {
    fun findByRevisionId(revisionId: Long): List<GtfsFareTransferRule>
}

interface GtfsAreaRepository : JpaRepository<GtfsArea, Long> {
    fun findByRevisionId(revisionId: Long): List<GtfsArea>
}

interface GtfsStopAreaRepository : JpaRepository<GtfsStopArea, Long> {
    fun findByRevisionId(revisionId: Long): List<GtfsStopArea>
}

interface GtfsNetworkRepository : JpaRepository<GtfsNetwork, Long> {
    fun findByRevisionId(revisionId: Long): List<GtfsNetwork>
}

interface GtfsRouteNetworkRepository : JpaRepository<GtfsRouteNetwork, Long> {
    fun findByRevisionId(revisionId: Long): List<GtfsRouteNetwork>
}
