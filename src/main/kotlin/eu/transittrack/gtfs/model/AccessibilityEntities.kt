package eu.transittrack.gtfs.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import org.springframework.data.jpa.repository.Query

/**
 * Typed JPA entities for the GTFS accessibility / flex files: pathways.txt,
 * levels.txt, location_groups.txt, location_group_stops.txt,
 * locations.geojson, booking_rules.txt.
 *
 * GTFS enum integers are stored as `SMALLINT`; the matching Kotlin `Int?` fields
 * carry `@JdbcTypeCode(SqlTypes.SMALLINT)` so Hibernate `ddl-auto: validate`
 * sees matching JDBC type codes. `booking_rule.prior_notice_last_time` /
 * `prior_notice_start_time` are stored as seconds-of-day (`INT`).
 *
 * `GtfsLocation.geometry` holds the raw GeoJSON geometry object as JSON text
 * mapped to a `JSONB` column via `@JdbcTypeCode(SqlTypes.JSON)`.
 */

@Entity
@Table(name = "gtfs_pathway")
class GtfsPathway(
    revisionId: Long,
    @Column(name = "pathway_id", nullable = false) var pathwayId: String,
    var fromStopId: String?,
    var toStopId: String?,
    @JdbcTypeCode(SqlTypes.SMALLINT) var pathwayMode: Int?,
    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "is_bidirectional")
    var isBidirectional: Int?,
    var length: Double?,
    var traversalTime: Int?,
    var stairCount: Int?,
    var maxSlope: Double?,
    var minWidth: Double?,
    var signpostedAs: String?,
    var reversedSignpostedAs: String?,
) : RevisionScoped(revisionId)

@Entity
@Table(name = "gtfs_level")
class GtfsLevel(
    revisionId: Long,
    @Column(name = "level_id", nullable = false) var levelId: String,
    var levelIndex: Double?,
    var levelName: String?,
) : RevisionScoped(revisionId)

@Entity
@Table(name = "gtfs_location_group")
class GtfsLocationGroup(
    revisionId: Long,
    @Column(name = "location_group_id", nullable = false) var locationGroupId: String,
    var locationGroupName: String?,
) : RevisionScoped(revisionId)

@Entity
@Table(name = "gtfs_location_group_stop")
class GtfsLocationGroupStop(
    revisionId: Long,
    @Column(name = "location_group_id", nullable = false) var locationGroupId: String,
    @Column(name = "stop_id", nullable = false) var stopId: String,
) : RevisionScoped(revisionId)

@Entity
@Table(name = "gtfs_location")
class GtfsLocation(
    revisionId: Long,
    @Column(name = "location_id", nullable = false) var locationId: String,
    var stopName: String?,
    var stopDesc: String?,
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "geometry") var geometry: String,
) : RevisionScoped(revisionId)

@Entity
@Table(name = "gtfs_booking_rule")
class GtfsBookingRule(
    revisionId: Long,
    @Column(name = "booking_rule_id", nullable = false) var bookingRuleId: String,
    @JdbcTypeCode(SqlTypes.SMALLINT) var bookingType: Int?,
    var priorNoticeDurationMin: Int?,
    var priorNoticeDurationMax: Int?,
    var priorNoticeLastDay: Int?,
    var priorNoticeLastTime: Int?,
    var priorNoticeStartDay: Int?,
    var priorNoticeStartTime: Int?,
    var priorNoticeServiceId: String?,
    var message: String?,
    var pickupMessage: String?,
    var dropOffMessage: String?,
    var phoneNumber: String?,
    var infoUrl: String?,
    var bookingUrl: String?,
) : RevisionScoped(revisionId)

interface GtfsPathwayRepository : RevisionScopedRepository<GtfsPathway, Long>

interface GtfsLevelRepository : RevisionScopedRepository<GtfsLevel, Long> {
    @Query("select l from GtfsLevel l where l.revisionId = :revisionId and l.levelId = :levelId")
    fun findByLevelId(revisionId: Long, levelId: String): GtfsLevel?
}

interface GtfsLocationGroupRepository : RevisionScopedRepository<GtfsLocationGroup, Long>

interface GtfsLocationGroupStopRepository : RevisionScopedRepository<GtfsLocationGroupStop, Long>

interface GtfsLocationRepository : RevisionScopedRepository<GtfsLocation, Long>

interface GtfsBookingRuleRepository : RevisionScopedRepository<GtfsBookingRule, Long>
