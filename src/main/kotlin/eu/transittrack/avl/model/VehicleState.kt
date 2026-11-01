package eu.transittrack.avl.model

import java.time.Instant
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.SequenceGenerator
import jakarta.persistence.Table

import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.GreaterOp
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.QueryBuilder
import org.jetbrains.exposed.v1.core.QueryParameter
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Sequence
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.append
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.intParam
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.max
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.core.wrapAsExpression
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

import eu.transittrack.db.EpochSecondsBetween
import eu.transittrack.db.RowTable
import eu.transittrack.db.bridge.ExposedBridge

@Entity
@Table(name = "vehicle_state")
class VehicleState(
    @Column(name = "feed_code", nullable = false) var feedCode: String,
    @Column(name = "vehicle_id", nullable = false) var vehicleId: String,
    @Column(name = "vehicle_label") var vehicleLabel: String?,
    @Column(name = "report_ts", nullable = false) var reportTs: Instant,
    @Column(nullable = false) var lat: Double,
    @Column(nullable = false) var lon: Double,
    @Column var bearing: Double?,
    @Column(name = "speed_mps") var speedMps: Double?,
    @JdbcTypeCode(SqlTypes.SMALLINT) @Column(name = "occupancy_status") var occupancyStatus: Int?,
    @Column(nullable = false) var matched: Boolean,
    @Column(nullable = false) var stale: Boolean,
    @Column(name = "consecutive_failures", nullable = false) var consecutiveFailures: Int,
    @Column(name = "revision_id") var revisionId: Long?,
    @Column(name = "trip_row_id") var tripRowId: Long?,
    @Column(name = "block_pk") var blockPk: Long?,
    @Column(name = "trip_pattern_id") var tripPatternId: Long?,
    @Column(name = "stop_path_index") var stopPathIndex: Int?,
    @Column(name = "distance_along_trip_m") var distanceAlongTripM: Double?,
    @Column(name = "schedule_adherence_sec") var scheduleAdherenceSec: Int?,
    @Column(name = "snapped_lat") var snappedLat: Double?,
    @Column(name = "snapped_lon") var snappedLon: Double?,
    @Column(name = "updated_at", nullable = false) var updatedAt: Instant,
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "vehicleStateSeq")
    @SequenceGenerator(name = "vehicleStateSeq", sequenceName = "vehicle_state_seq", allocationSize = 50)
    var id: Long? = null,
)

object VehicleStateTable : RowTable<VehicleState>("vehicle_state") {
    val id = long("id").autoIncrement(Sequence("vehicle_state_seq", startWith = 1, incrementBy = 50))
    val feedCode =
        varchar("feed_code", 64).references(
            AvlFeedTable.code,
            onDelete = ReferenceOption.CASCADE,
            onUpdate = ReferenceOption.NO_ACTION,
            fkName = "fk_vehicle_state_feed",
        )
    val vehicleId = varchar("vehicle_id", 255)
    val vehicleLabel = varchar("vehicle_label", 255).nullable()
    val reportTs = timestampTz("report_ts")
    val lat = double("lat")
    val lon = double("lon")
    val bearing = double("bearing").nullable()
    val speedMps = double("speed_mps").nullable()
    val occupancyStatus = short("occupancy_status").nullable()
    val matched = bool("matched")
    val stale = bool("stale").default(false)
    val consecutiveFailures = integer("consecutive_failures").default(0)
    val revisionId = long("revision_id").nullable()
    val tripRowId = long("trip_row_id").nullable()
    val blockPk = long("block_pk").nullable()
    val tripPatternId = long("trip_pattern_id").nullable()
    val stopPathIndex = integer("stop_path_index").nullable()
    val distanceAlongTripM = double("distance_along_trip_m").nullable()
    val scheduleAdherenceSec = integer("schedule_adherence_sec").nullable()
    val snappedLat = double("snapped_lat").nullable()
    val snappedLon = double("snapped_lon").nullable()
    val updatedAt = timestampTz("updated_at")

    override val primaryKey = PrimaryKey(id, name = "vehicle_state_pkey")

    init {
        uniqueIndex("uq_vehicle_state_feed_vehicle", feedCode, vehicleId)
        index("ix_vehicle_state_feed_updated", false, feedCode, updatedAt)
    }

    override fun read(row: ResultRow) =
        VehicleState(
            feedCode = row[feedCode],
            vehicleId = row[vehicleId],
            vehicleLabel = row[vehicleLabel],
            reportTs = row[reportTs],
            lat = row[lat],
            lon = row[lon],
            bearing = row[bearing],
            speedMps = row[speedMps],
            occupancyStatus = row[occupancyStatus]?.toInt(),
            matched = row[matched],
            stale = row[stale],
            consecutiveFailures = row[consecutiveFailures],
            revisionId = row[revisionId],
            tripRowId = row[tripRowId],
            blockPk = row[blockPk],
            tripPatternId = row[tripPatternId],
            stopPathIndex = row[stopPathIndex],
            distanceAlongTripM = row[distanceAlongTripM],
            scheduleAdherenceSec = row[scheduleAdherenceSec],
            snappedLat = row[snappedLat],
            snappedLon = row[snappedLon],
            updatedAt = row[updatedAt],
            id = row[id],
        )

    override fun bind(
        s: UpdateBuilder<*>,
        row: VehicleState,
    ) {
        s[feedCode] = row.feedCode
        s[vehicleId] = row.vehicleId
        s[vehicleLabel] = row.vehicleLabel
        s[reportTs] = row.reportTs
        s[lat] = row.lat
        s[lon] = row.lon
        s[bearing] = row.bearing
        s[speedMps] = row.speedMps
        s[occupancyStatus] = row.occupancyStatus?.toShort()
        s[matched] = row.matched
        s[stale] = row.stale
        s[consecutiveFailures] = row.consecutiveFailures
        s[revisionId] = row.revisionId
        s[tripRowId] = row.tripRowId
        s[blockPk] = row.blockPk
        s[tripPatternId] = row.tripPatternId
        s[stopPathIndex] = row.stopPathIndex
        s[distanceAlongTripM] = row.distanceAlongTripM
        s[scheduleAdherenceSec] = row.scheduleAdherenceSec
        s[snappedLat] = row.snappedLat
        s[snappedLon] = row.snappedLon
        s[updatedAt] = row.updatedAt
    }
}

/** `CAST(? AS TIMESTAMP WITH TIME ZONE)`, as Hibernate binds an `Instant` parameter in arithmetic. */
private class TimestampTzParam(
    private val value: Instant,
) : Expression<Instant>() {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) =
        queryBuilder {
            append("CAST(", QueryParameter(value, VehicleStateTable.updatedAt.columnType), " AS TIMESTAMP WITH TIME ZONE)")
        }
}

@Repository
@ExposedBridge
class VehicleStateRepository {
    fun findByFeedCodeAndVehicleId(
        feedCode: String,
        vehicleId: String,
    ): VehicleState? =
        VehicleStateTable
            .selectAll()
            .where { (VehicleStateTable.feedCode eq feedCode) and (VehicleStateTable.vehicleId eq vehicleId) }
            .orderBy(VehicleStateTable.updatedAt, SortOrder.DESC)
            .singleOrNull()
            ?.let(VehicleStateTable::read)

    fun findByFeedCodeOrderByUpdatedAtDesc(feedCode: String): List<VehicleState> =
        VehicleStateTable
            .selectAll()
            .where { VehicleStateTable.feedCode eq feedCode }
            .orderBy(VehicleStateTable.updatedAt, SortOrder.DESC)
            .map(VehicleStateTable::read)

    fun findByFeedCode(feedCode: String): List<VehicleState> =
        VehicleStateTable
            .selectAll()
            .where { VehicleStateTable.feedCode eq feedCode }
            .map(VehicleStateTable::read)

    /**
     * `(seconds from the vehicle's newest avl_report.ts to asOf) > thresholdSec`, via a correlated
     * `max(ts)` subquery on the updated `vehicle_state` row; false for a vehicle with no reports.
     */
    private fun silentLongerThan(
        thresholdSec: Int,
        asOf: Instant,
    ): Op<Boolean> {
        val newestReportTs =
            wrapAsExpression<Instant>(
                AvlReportTable
                    .select(AvlReportTable.ts.max())
                    .where {
                        (AvlReportTable.feedCode eq VehicleStateTable.feedCode) and
                            (AvlReportTable.vehicleId eq VehicleStateTable.vehicleId)
                    },
            )
        return GreaterOp(EpochSecondsBetween(newestReportTs, TimestampTzParam(asOf)), intParam(thresholdSec))
    }

    /**
     * Drops the trip/block assignment and flips `matched -> false` for [feedCode]'s vehicles silent
     * past [thresholdSec] as of [asOf]. Used by [eu.transittrack.avl.match.AvlSilentVehicleSweeper],
     * once per feed with [thresholdSec] precomputed in application code — a correlated subquery on
     * [AvlFeed] here would otherwise be re-evaluated for every [VehicleState] row on every sweep tick.
     */
    @Transactional
    fun unmatchSilent(
        feedCode: String,
        thresholdSec: Int,
        asOf: Instant,
    ): Int =
        VehicleStateTable.update({
            (VehicleStateTable.feedCode eq feedCode) and VehicleStateTable.tripRowId.isNotNull() and silentLongerThan(thresholdSec, asOf)
        }) {
            it[matched] = false
            it[stale] = true
            it[revisionId] = null
            it[tripRowId] = null
            it[blockPk] = null
            it[tripPatternId] = null
            it[stopPathIndex] = null
            it[distanceAlongTripM] = null
            it[scheduleAdherenceSec] = null
            it[snappedLat] = null
            it[snappedLon] = null
        }

    /** Flags [feedCode]'s still-matched vehicles `stale = true` once they've been silent past [thresholdSec]. */
    @Transactional
    fun staleSilent(
        feedCode: String,
        thresholdSec: Int,
        asOf: Instant,
    ): Int =
        VehicleStateTable.update({
            (VehicleStateTable.feedCode eq feedCode) and (VehicleStateTable.matched eq true) and (VehicleStateTable.stale eq false) and
                silentLongerThan(thresholdSec, asOf)
        }) {
            it[stale] = true
        }

    /** Age-based retention: deletes states not updated since [cutoff]. */
    fun deleteUpdatedBefore(cutoff: Instant): Int = VehicleStateTable.deleteWhere { updatedAt less cutoff }

    fun findAll(): List<VehicleState> = VehicleStateTable.selectAll().map(VehicleStateTable::read)

    /** Inserts when [row] has no id, else updates it in place; returns [row] with its id set. */
    fun save(row: VehicleState): VehicleState {
        val id = row.id
        if (id == null) {
            row.id = VehicleStateTable.insert { VehicleStateTable.bind(it, row) }[VehicleStateTable.id]
        } else {
            VehicleStateTable.update({ VehicleStateTable.id eq id }) { VehicleStateTable.bind(it, row) }
        }
        return row
    }

    fun deleteAll() {
        VehicleStateTable.deleteAll()
    }
}
