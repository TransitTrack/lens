package eu.transittrack.avl.model

import java.time.Instant
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.SequenceGenerator
import jakarta.persistence.Table

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Sequence
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.min
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

import eu.transittrack.db.RowTable
import eu.transittrack.db.bridge.ExposedBridge
import eu.transittrack.util.paging.PageRequest

/** Ordinal 0 = PENDING must match the `vehicle_match.prediction_status` column default. */
enum class PredictionStatus { PENDING, DONE, FAILED }

@Entity
@Table(name = "vehicle_match")
class VehicleMatch(
    @Column(name = "avl_report_id", nullable = false) var avlReportId: Long,
    @Column(name = "feed_code", nullable = false) var feedCode: String,
    @Column(name = "vehicle_id", nullable = false) var vehicleId: String,
    @Column(nullable = false) var ts: Instant,
    @Column(name = "revision_id", nullable = false) var revisionId: Long,
    @Column(name = "trip_row_id", nullable = false) var tripRowId: Long,
    @Column(name = "block_pk") var blockPk: Long?,
    @Column(name = "trip_pattern_id") var tripPatternId: Long?,
    @Column(name = "stop_path_index", nullable = false) var stopPathIndex: Int,
    @Column(name = "distance_along_trip_m", nullable = false) var distanceAlongTripM: Double,
    @Column(name = "deviation_m", nullable = false) var deviationM: Double,
    @Column(name = "schedule_adherence_sec") var scheduleAdherenceSec: Int?,
    @Column(name = "snapped_lat", nullable = false) var snappedLat: Double,
    @Column(name = "snapped_lon", nullable = false) var snappedLon: Double,
    @Column var heading: Double?,
    @Column var score: Double?,
    @Column(name = "created_at", nullable = false) var createdAt: Instant,
    @Enumerated
    @Column(name = "prediction_status", nullable = false)
    var predictionStatus: PredictionStatus = PredictionStatus.PENDING,
    @Column(name = "predicted_at") var predictedAt: Instant? = null,
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "vehicleMatchSeq")
    @SequenceGenerator(name = "vehicleMatchSeq", sequenceName = "vehicle_match_seq", allocationSize = 50)
    var id: Long? = null,
)

data class PendingPredictionMetrics(
    val feedCode: String,
    val pendingCount: Long,
    val oldestCreatedAt: Instant?,
)

object VehicleMatchTable : RowTable<VehicleMatch>("vehicle_match") {
    val id = long("id").autoIncrement(Sequence("vehicle_match_seq", startWith = 1, incrementBy = 50))
    val avlReportId =
        long("avl_report_id").references(
            AvlReportTable.id,
            onDelete = ReferenceOption.CASCADE,
            onUpdate = ReferenceOption.NO_ACTION,
            fkName = "fk_vehicle_match_report",
        )
    val feedCode = varchar("feed_code", 64)
    val vehicleId = varchar("vehicle_id", 255)
    val ts = timestampTz("ts")
    val revisionId = long("revision_id")
    val tripRowId = long("trip_row_id")
    val blockPk = long("block_pk").nullable()
    val tripPatternId = long("trip_pattern_id").nullable()
    val stopPathIndex = integer("stop_path_index")
    val distanceAlongTripM = double("distance_along_trip_m")
    val deviationM = double("deviation_m")
    val scheduleAdherenceSec = integer("schedule_adherence_sec").nullable()
    val snappedLat = double("snapped_lat")
    val snappedLon = double("snapped_lon")
    val heading = double("heading").nullable()
    val score = double("score").nullable()
    val createdAt = timestampTz("created_at").defaultNow()
    val predictionStatus = shortOrdinal<PredictionStatus>("prediction_status").default(PredictionStatus.PENDING)
    val predictedAt = timestampTz("predicted_at").nullable()

    override val primaryKey = PrimaryKey(id, name = "vehicle_match_pkey")

    init {
        index("ix_vehicle_match_feed_vehicle_ts", false, feedCode, vehicleId, ts)
        index("ix_vehicle_match_created", false, createdAt)
        index("ix_vehicle_match_report", false, avlReportId)
        index("ix_vehicle_match_pending", false, feedCode, ts, vehicleId) { predictionStatus eq PredictionStatus.PENDING }
    }

    override fun read(row: ResultRow) =
        VehicleMatch(
            avlReportId = row[avlReportId],
            feedCode = row[feedCode],
            vehicleId = row[vehicleId],
            ts = row[ts],
            revisionId = row[revisionId],
            tripRowId = row[tripRowId],
            blockPk = row[blockPk],
            tripPatternId = row[tripPatternId],
            stopPathIndex = row[stopPathIndex],
            distanceAlongTripM = row[distanceAlongTripM],
            deviationM = row[deviationM],
            scheduleAdherenceSec = row[scheduleAdherenceSec],
            snappedLat = row[snappedLat],
            snappedLon = row[snappedLon],
            heading = row[heading],
            score = row[score],
            createdAt = row[createdAt],
            predictionStatus = row[predictionStatus],
            predictedAt = row[predictedAt],
            id = row[id],
        )

    override fun bind(
        s: UpdateBuilder<*>,
        row: VehicleMatch,
    ) {
        s[avlReportId] = row.avlReportId
        s[feedCode] = row.feedCode
        s[vehicleId] = row.vehicleId
        s[ts] = row.ts
        s[revisionId] = row.revisionId
        s[tripRowId] = row.tripRowId
        s[blockPk] = row.blockPk
        s[tripPatternId] = row.tripPatternId
        s[stopPathIndex] = row.stopPathIndex
        s[distanceAlongTripM] = row.distanceAlongTripM
        s[deviationM] = row.deviationM
        s[scheduleAdherenceSec] = row.scheduleAdherenceSec
        s[snappedLat] = row.snappedLat
        s[snappedLon] = row.snappedLon
        s[heading] = row.heading
        s[score] = row.score
        s[createdAt] = row.createdAt
        s[predictionStatus] = row.predictionStatus
        s[predictedAt] = row.predictedAt
    }
}

@Repository
@ExposedBridge
class VehicleMatchRepository {
    fun findByAvlReportIdIn(avlReportIds: Collection<Long>): List<VehicleMatch> =
        VehicleMatchTable
            .selectAll()
            .where { VehicleMatchTable.avlReportId inList avlReportIds }
            .map(VehicleMatchTable::read)

    fun findByVehicle(
        feedCode: String,
        vehicleId: String,
        page: PageRequest,
    ): List<VehicleMatch> =
        VehicleMatchTable
            .selectAll()
            .where { (VehicleMatchTable.feedCode eq feedCode) and (VehicleMatchTable.vehicleId eq vehicleId) }
            .orderBy(VehicleMatchTable.ts, SortOrder.DESC)
            .limit(page.size)
            .offset(page.offset)
            .map(VehicleMatchTable::read)

    fun findByVehicleSince(
        feedCode: String,
        vehicleId: String,
        ts: Instant,
        page: PageRequest,
    ): List<VehicleMatch> =
        VehicleMatchTable
            .selectAll()
            .where {
                (VehicleMatchTable.feedCode eq feedCode) and (VehicleMatchTable.vehicleId eq vehicleId) and
                    (VehicleMatchTable.ts greaterEq ts)
            }.orderBy(VehicleMatchTable.ts, SortOrder.DESC)
            .limit(page.size)
            .offset(page.offset)
            .map(VehicleMatchTable::read)

    /** The vehicle's most recent match strictly before [ts] — used as the crossing-detection "prev"
     * when a claimed batch's oldest row for this vehicle isn't the vehicle's very first match. Relies
     * on [findClaimBatch] claiming PENDING rows oldest-first per vehicle: any earlier row for the same
     * vehicle was necessarily already processed in a prior batch, so this is never itself PENDING. */
    fun findPreviousForVehicle(
        feedCode: String,
        vehicleId: String,
        ts: Instant,
    ): VehicleMatch? =
        VehicleMatchTable
            .selectAll()
            .where {
                (VehicleMatchTable.feedCode eq feedCode) and (VehicleMatchTable.vehicleId eq vehicleId) and
                    (VehicleMatchTable.ts less ts)
            }.orderBy(VehicleMatchTable.ts, SortOrder.DESC)
            .limit(1)
            .singleOrNull()
            ?.let(VehicleMatchTable::read)

    // Ordered by ts first (global FIFO), vehicleId only as a tiebreaker: ordering by vehicleId first
    // sorts vehicle IDs as strings (e.g. "1002" < "547" lexicographically), so once a backlog exceeds
    // `limit` rows, the same lexicographically-early vehicle IDs would win every single claim and
    // starve the rest indefinitely. Sorting by ts first still preserves the oldest-first-per-vehicle
    // guarantee the comment above relies on, since any older row for the same vehicle necessarily
    // sorts before a newer one.
    fun findClaimBatch(
        feedCode: String,
        limit: Int,
    ): List<VehicleMatch> =
        VehicleMatchTable
            .selectAll()
            .where { (VehicleMatchTable.predictionStatus eq PredictionStatus.PENDING) and (VehicleMatchTable.feedCode eq feedCode) }
            .orderBy(VehicleMatchTable.ts to SortOrder.ASC, VehicleMatchTable.vehicleId to SortOrder.ASC)
            .limit(limit)
            .map(VehicleMatchTable::read)

    @Transactional
    fun markPredicted(
        ids: List<Long>,
        status: PredictionStatus,
        at: Instant,
    ) {
        VehicleMatchTable.update({ VehicleMatchTable.id inList ids }) {
            it[predictionStatus] = status
            it[predictedAt] = at
        }
    }

    fun pendingPredictionMetricsByFeed(): List<PendingPredictionMetrics> {
        val pendingCount = VehicleMatchTable.id.count()
        val oldestCreatedAt = VehicleMatchTable.createdAt.min()
        return VehicleMatchTable
            .select(VehicleMatchTable.feedCode, pendingCount, oldestCreatedAt)
            .where { VehicleMatchTable.predictionStatus eq PredictionStatus.PENDING }
            .groupBy(VehicleMatchTable.feedCode)
            .map { PendingPredictionMetrics(it[VehicleMatchTable.feedCode], it[pendingCount], it[oldestCreatedAt]) }
    }

    fun findInWindow(
        feedCode: String,
        from: Instant,
        until: Instant,
    ): List<VehicleMatch> =
        VehicleMatchTable
            .selectAll()
            .where {
                (VehicleMatchTable.feedCode eq feedCode) and (VehicleMatchTable.ts greaterEq from) and
                    (VehicleMatchTable.ts less until)
            }.orderBy(VehicleMatchTable.vehicleId to SortOrder.ASC, VehicleMatchTable.ts to SortOrder.ASC)
            .map(VehicleMatchTable::read)

    fun findForTripsInWindow(
        feedCode: String,
        tripRowIds: Collection<Long>,
        from: Instant,
        until: Instant,
    ): List<VehicleMatch> =
        VehicleMatchTable
            .selectAll()
            .where {
                (VehicleMatchTable.feedCode eq feedCode) and (VehicleMatchTable.tripRowId inList tripRowIds) and
                    (VehicleMatchTable.ts greaterEq from) and (VehicleMatchTable.ts less until)
            }.orderBy(VehicleMatchTable.vehicleId to SortOrder.ASC, VehicleMatchTable.ts to SortOrder.ASC)
            .map(VehicleMatchTable::read)

    /** Age-based retention: deletes matches created before [cutoff]. */
    fun deleteCreatedBefore(cutoff: Instant): Int = VehicleMatchTable.deleteWhere { createdAt less cutoff }

    /** Inserts when [row] has no id, else updates it in place; returns [row] with its id set. */
    fun save(row: VehicleMatch): VehicleMatch {
        val id = row.id
        if (id == null) {
            row.id = VehicleMatchTable.insert { VehicleMatchTable.bind(it, row) }[VehicleMatchTable.id]
        } else {
            VehicleMatchTable.update({ VehicleMatchTable.id eq id }) { VehicleMatchTable.bind(it, row) }
        }
        return row
    }

    fun deleteAll() {
        VehicleMatchTable.deleteAll()
    }
}
