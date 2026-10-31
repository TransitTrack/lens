package eu.transittrack.gtfs.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.json.jsonb
import org.springframework.stereotype.Repository

import eu.transittrack.db.bridge.ExposedBridge

/**
 * Typed JPA entities for the GTFS accessibility / flex files: pathways.txt, levels.txt,
 * location_groups.txt, location_group_stops.txt, locations.geojson, booking_rules.txt.
 *
 * GTFS enum integers are stored as `SMALLINT`; the matching Kotlin `Int?` fields carry
 * `@JdbcTypeCode(SqlTypes.SMALLINT)` so Hibernate `ddl-auto: validate` sees matching JDBC type
 * codes. `booking_rule.prior_notice_last_time` / `prior_notice_start_time` are stored as
 * seconds-of-day (`INT`).
 *
 * `GtfsLocation.geometry` holds the raw GeoJSON geometry object as JSON text mapped to a `JSONB`
 * column via `@JdbcTypeCode(SqlTypes.JSON)`.
 */
@Entity
@Table(name = "pathways")
class Pathway(
    revisionId: Long,
    @Column(name = "pathway_id", nullable = false) var pathwayId: String,
    var fromStopId: String?,
    var toStopId: String?,
    @JdbcTypeCode(SqlTypes.SMALLINT) var pathwayMode: Int?,
    @JdbcTypeCode(SqlTypes.SMALLINT) @Column(name = "is_bidirectional") var isBidirectional: Int?,
    var length: Double?,
    var traversalTime: Int?,
    var stairCount: Int?,
    var maxSlope: Double?,
    var minWidth: Double?,
    var signpostedAs: String?,
    var reversedSignpostedAs: String?,
) : RevisionScoped(revisionId)

@Entity
@Table(name = "levels")
class Level(
    revisionId: Long,
    @Column(name = "level_id", nullable = false) var levelId: String,
    var levelIndex: Double?,
    var levelName: String?,
) : RevisionScoped(revisionId)

@Entity
@Table(name = "location_groups")
class LocationGroup(
    revisionId: Long,
    @Column(name = "location_group_id", nullable = false) var locationGroupId: String,
    var locationGroupName: String?,
) : RevisionScoped(revisionId)

@Entity
@Table(name = "location_group_stops")
class LocationGroupStop(
    revisionId: Long,
    @Column(name = "location_group_id", nullable = false) var locationGroupId: String,
    @Column(name = "stop_id", nullable = false) var stopId: String,
) : RevisionScoped(revisionId)

@Entity
@Table(name = "locations")
class Location(
    revisionId: Long,
    @Column(name = "location_id", nullable = false) var locationId: String,
    var stopName: String?,
    var stopDesc: String?,
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "geometry") var geometry: String,
) : RevisionScoped(revisionId)

@Entity
@Table(name = "booking_rules")
class BookingRule(
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

object PathwayTable : RevisionScopedTable<Pathway>("pathways", Pathway::class.java, "fk_pathway_rev") {
    val pathwayId = varchar("pathway_id", 255)
    val fromStopId = varchar("from_stop_id", 255).nullable()
    val toStopId = varchar("to_stop_id", 255).nullable()
    val pathwayMode = short("pathway_mode").nullable()
    val isBidirectional = short("is_bidirectional").nullable()
    val length = double("length").nullable()
    val traversalTime = integer("traversal_time").nullable()
    val stairCount = integer("stair_count").nullable()
    val maxSlope = double("max_slope").nullable()
    val minWidth = double("min_width").nullable()
    val signpostedAs = varchar("signposted_as", 255).nullable()
    val reversedSignpostedAs = varchar("reversed_signposted_as", 255).nullable()

    init {
        uniqueIndex("uq_pathway_rev_id", revisionId, pathwayId)
        index("ix_pathway_rev", false, revisionId)
    }

    override fun read(row: ResultRow) =
        Pathway(
            revisionId = row[revisionId],
            pathwayId = row[pathwayId],
            fromStopId = row[fromStopId],
            toStopId = row[toStopId],
            pathwayMode = row[pathwayMode]?.toInt(),
            isBidirectional = row[isBidirectional]?.toInt(),
            length = row[length],
            traversalTime = row[traversalTime],
            stairCount = row[stairCount],
            maxSlope = row[maxSlope],
            minWidth = row[minWidth],
            signpostedAs = row[signpostedAs],
            reversedSignpostedAs = row[reversedSignpostedAs],
        ).withId(row)

    override fun bind(
        s: UpdateBuilder<*>,
        row: Pathway,
    ) {
        s[revisionId] = row.revisionId
        s[pathwayId] = row.pathwayId
        s[fromStopId] = row.fromStopId
        s[toStopId] = row.toStopId
        s[pathwayMode] = row.pathwayMode?.toShort()
        s[isBidirectional] = row.isBidirectional?.toShort()
        s[length] = row.length
        s[traversalTime] = row.traversalTime
        s[stairCount] = row.stairCount
        s[maxSlope] = row.maxSlope
        s[minWidth] = row.minWidth
        s[signpostedAs] = row.signpostedAs
        s[reversedSignpostedAs] = row.reversedSignpostedAs
    }
}

object LevelTable : RevisionScopedTable<Level>("levels", Level::class.java, "fk_level_rev") {
    val levelId = varchar("level_id", 255)
    val levelIndex = double("level_index").nullable()
    val levelName = varchar("level_name", 255).nullable()

    init {
        uniqueIndex("uq_level_rev_id", revisionId, levelId)
    }

    override fun read(row: ResultRow) =
        Level(revisionId = row[revisionId], levelId = row[levelId], levelIndex = row[levelIndex], levelName = row[levelName]).withId(row)

    override fun bind(
        s: UpdateBuilder<*>,
        row: Level,
    ) {
        s[revisionId] = row.revisionId
        s[levelId] = row.levelId
        s[levelIndex] = row.levelIndex
        s[levelName] = row.levelName
    }
}

object LocationGroupTable : RevisionScopedTable<LocationGroup>("location_groups", LocationGroup::class.java, "fk_locgroup_rev") {
    val locationGroupId = varchar("location_group_id", 255)
    val locationGroupName = varchar("location_group_name", 255).nullable()

    init {
        uniqueIndex("uq_locgroup_rev_id", revisionId, locationGroupId)
    }

    override fun read(row: ResultRow) =
        LocationGroup(
            revisionId = row[revisionId],
            locationGroupId = row[locationGroupId],
            locationGroupName = row[locationGroupName],
        ).withId(row)

    override fun bind(
        s: UpdateBuilder<*>,
        row: LocationGroup,
    ) {
        s[revisionId] = row.revisionId
        s[locationGroupId] = row.locationGroupId
        s[locationGroupName] = row.locationGroupName
    }
}

object LocationGroupStopTable :
    RevisionScopedTable<LocationGroupStop>("location_group_stops", LocationGroupStop::class.java, "fk_locgroupstop_rev") {
    val locationGroupId = varchar("location_group_id", 255)
    val stopId = varchar("stop_id", 255)

    init {
        uniqueIndex("uq_locgroupstop_rev", revisionId, locationGroupId, stopId)
    }

    override fun read(row: ResultRow) =
        LocationGroupStop(revisionId = row[revisionId], locationGroupId = row[locationGroupId], stopId = row[stopId]).withId(row)

    override fun bind(
        s: UpdateBuilder<*>,
        row: LocationGroupStop,
    ) {
        s[revisionId] = row.revisionId
        s[locationGroupId] = row.locationGroupId
        s[stopId] = row.stopId
    }
}

object LocationTable : RevisionScopedTable<Location>("locations", Location::class.java, "fk_location_rev") {
    val locationId = varchar("location_id", 255)
    val stopName = varchar("stop_name", 500).nullable()
    val stopDesc = text("stop_desc").nullable()

    // Raw GeoJSON text, as Hibernate's String + SqlTypes.JSON mapping stored it.
    val geometry = jsonb<String>("geometry", { it }, { it })

    init {
        uniqueIndex("uq_location_rev_id", revisionId, locationId)
    }

    override fun read(row: ResultRow) =
        Location(
            revisionId = row[revisionId],
            locationId = row[locationId],
            stopName = row[stopName],
            stopDesc = row[stopDesc],
            geometry = row[geometry],
        ).withId(row)

    override fun bind(
        s: UpdateBuilder<*>,
        row: Location,
    ) {
        s[revisionId] = row.revisionId
        s[locationId] = row.locationId
        s[stopName] = row.stopName
        s[stopDesc] = row.stopDesc
        s[geometry] = row.geometry
    }
}

object BookingRuleTable : RevisionScopedTable<BookingRule>("booking_rules", BookingRule::class.java, "fk_booking_rule_rev") {
    val bookingRuleId = varchar("booking_rule_id", 255)
    val bookingType = short("booking_type").nullable()
    val priorNoticeDurationMin = integer("prior_notice_duration_min").nullable()
    val priorNoticeDurationMax = integer("prior_notice_duration_max").nullable()
    val priorNoticeLastDay = integer("prior_notice_last_day").nullable()
    val priorNoticeLastTime = integer("prior_notice_last_time").nullable()
    val priorNoticeStartDay = integer("prior_notice_start_day").nullable()
    val priorNoticeStartTime = integer("prior_notice_start_time").nullable()
    val priorNoticeServiceId = varchar("prior_notice_service_id", 255).nullable()
    val message = text("message").nullable()
    val pickupMessage = text("pickup_message").nullable()
    val dropOffMessage = text("drop_off_message").nullable()
    val phoneNumber = varchar("phone_number", 64).nullable()
    val infoUrl = text("info_url").nullable()
    val bookingUrl = text("booking_url").nullable()

    init {
        uniqueIndex("uq_booking_rule_rev_id", revisionId, bookingRuleId)
    }

    override fun read(row: ResultRow) =
        BookingRule(
            revisionId = row[revisionId],
            bookingRuleId = row[bookingRuleId],
            bookingType = row[bookingType]?.toInt(),
            priorNoticeDurationMin = row[priorNoticeDurationMin],
            priorNoticeDurationMax = row[priorNoticeDurationMax],
            priorNoticeLastDay = row[priorNoticeLastDay],
            priorNoticeLastTime = row[priorNoticeLastTime],
            priorNoticeStartDay = row[priorNoticeStartDay],
            priorNoticeStartTime = row[priorNoticeStartTime],
            priorNoticeServiceId = row[priorNoticeServiceId],
            message = row[message],
            pickupMessage = row[pickupMessage],
            dropOffMessage = row[dropOffMessage],
            phoneNumber = row[phoneNumber],
            infoUrl = row[infoUrl],
            bookingUrl = row[bookingUrl],
        ).withId(row)

    override fun bind(
        s: UpdateBuilder<*>,
        row: BookingRule,
    ) {
        s[revisionId] = row.revisionId
        s[bookingRuleId] = row.bookingRuleId
        s[bookingType] = row.bookingType?.toShort()
        s[priorNoticeDurationMin] = row.priorNoticeDurationMin
        s[priorNoticeDurationMax] = row.priorNoticeDurationMax
        s[priorNoticeLastDay] = row.priorNoticeLastDay
        s[priorNoticeLastTime] = row.priorNoticeLastTime
        s[priorNoticeStartDay] = row.priorNoticeStartDay
        s[priorNoticeStartTime] = row.priorNoticeStartTime
        s[priorNoticeServiceId] = row.priorNoticeServiceId
        s[message] = row.message
        s[pickupMessage] = row.pickupMessage
        s[dropOffMessage] = row.dropOffMessage
        s[phoneNumber] = row.phoneNumber
        s[infoUrl] = row.infoUrl
        s[bookingUrl] = row.bookingUrl
    }
}

@Repository
@ExposedBridge
class PathwayRepository : RevisionScopedRepository<Pathway>(PathwayTable)

@Repository
@ExposedBridge
class LevelRepository : RevisionScopedRepository<Level>(LevelTable) {
    fun findByLevelId(
        revisionId: Long,
        levelId: String,
    ): Level? =
        LevelTable
            .selectAll()
            .where { (LevelTable.revisionId eq revisionId) and (LevelTable.levelId eq levelId) }
            .singleOrNull()
            ?.let(LevelTable::read)
}

@Repository
@ExposedBridge
class LocationGroupRepository : RevisionScopedRepository<LocationGroup>(LocationGroupTable)

@Repository
@ExposedBridge
class LocationGroupStopRepository : RevisionScopedRepository<LocationGroupStop>(LocationGroupStopTable)

@Repository
@ExposedBridge
class LocationRepository : RevisionScopedRepository<Location>(LocationTable)

@Repository
@ExposedBridge
class BookingRuleRepository : RevisionScopedRepository<BookingRule>(BookingRuleTable)
