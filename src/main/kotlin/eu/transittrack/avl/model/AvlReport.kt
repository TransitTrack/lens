package eu.transittrack.avl.model

import java.time.Instant
import java.time.LocalDate

import org.jetbrains.exposed.v1.core.Op
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
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.max
import org.jetbrains.exposed.v1.core.min
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.javatime.date
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

import eu.transittrack.db.RowTable
import eu.transittrack.util.paging.PageRequest
import eu.transittrack.util.paging.PageResult

/** Ordinal 0 = PENDING must match the `avl_report.match_status` column default. */
enum class MatchStatus { PENDING, MATCHED, UNMATCHED, SKIPPED }

class AvlReportRow(
    var feedCode: String,
    var vehicleId: String,
    var vehicleLabel: String?,
    var ts: Instant,
    var lat: Double,
    var lon: Double,
    var bearing: Double?,
    var speedMps: Double?,
    var odometerM: Double?,
    var descTripId: String?,
    var descRouteId: String?,
    var descDirectionId: Int?,
    var descStartDate: LocalDate?,
    var descStartTimeSec: Int?,
    var descScheduleRelationship: Int?,
    var currentStopSequence: Int?,
    var currentStopId: String?,
    var currentStatus: Int?,
    var occupancyStatus: Int?,
    var congestionLevel: Int?,
    var matchStatus: MatchStatus,
    var matchedAt: Instant?,
    var createdAt: Instant,
    var id: Long? = null,
)

data class VehicleTsProjection(
    val vehicleId: String,
    val ts: Instant,
)

data class PendingAvlMetrics(
    val feedCode: String,
    val pendingCount: Long,
    val oldestCreatedAt: Instant?,
)

object AvlReportTable : RowTable<AvlReportRow>("avl_report") {
    val id = long("id").autoIncrement(Sequence("avl_report_seq", startWith = 1, incrementBy = 50))
    val feedCode =
        varchar("feed_code", 64).references(
            AvlFeedTable.code,
            onDelete = ReferenceOption.CASCADE,
            onUpdate = ReferenceOption.NO_ACTION,
            fkName = "fk_avl_report_feed",
        )
    val vehicleId = varchar("vehicle_id", 255)
    val vehicleLabel = varchar("vehicle_label", 255).nullable()
    val ts = timestampTz("ts")
    val lat = double("lat")
    val lon = double("lon")
    val bearing = double("bearing").nullable()
    val speedMps = double("speed_mps").nullable()
    val odometerM = double("odometer_m").nullable()
    val descTripId = varchar("desc_trip_id", 255).nullable()
    val descRouteId = varchar("desc_route_id", 255).nullable()
    val descDirectionId = short("desc_direction_id").nullable()
    val descStartDate = date("desc_start_date").nullable()
    val descStartTimeSec = integer("desc_start_time_sec").nullable()
    val descScheduleRelationship = short("desc_schedule_relationship").nullable()
    val currentStopSequence = integer("current_stop_sequence").nullable()
    val currentStopId = varchar("current_stop_id", 255).nullable()
    val currentStatus = short("current_status").nullable()
    val occupancyStatus = short("occupancy_status").nullable()
    val congestionLevel = short("congestion_level").nullable()
    val matchStatus = shortOrdinal<MatchStatus>("match_status").default(MatchStatus.PENDING)
    val matchedAt = timestampTz("matched_at").nullable()
    val createdAt = timestampTz("created_at").defaultNow()

    override val primaryKey = PrimaryKey(id, name = "avl_report_pkey")

    init {
        index("ix_avl_report_feed_vehicle_ts", false, feedCode, vehicleId, ts)
        index("ix_avl_report_claim", false, matchStatus, id)
        index("ix_avl_report_created", false, createdAt)
    }

    override fun read(row: ResultRow) =
        AvlReportRow(
            feedCode = row[feedCode],
            vehicleId = row[vehicleId],
            vehicleLabel = row[vehicleLabel],
            ts = row[ts],
            lat = row[lat],
            lon = row[lon],
            bearing = row[bearing],
            speedMps = row[speedMps],
            odometerM = row[odometerM],
            descTripId = row[descTripId],
            descRouteId = row[descRouteId],
            descDirectionId = row[descDirectionId]?.toInt(),
            descStartDate = row[descStartDate],
            descStartTimeSec = row[descStartTimeSec],
            descScheduleRelationship = row[descScheduleRelationship]?.toInt(),
            currentStopSequence = row[currentStopSequence],
            currentStopId = row[currentStopId],
            currentStatus = row[currentStatus]?.toInt(),
            occupancyStatus = row[occupancyStatus]?.toInt(),
            congestionLevel = row[congestionLevel]?.toInt(),
            matchStatus = row[matchStatus],
            matchedAt = row[matchedAt],
            createdAt = row[createdAt],
            id = row[id],
        )

    override fun bind(
        s: UpdateBuilder<*>,
        row: AvlReportRow,
    ) {
        s[feedCode] = row.feedCode
        s[vehicleId] = row.vehicleId
        s[vehicleLabel] = row.vehicleLabel
        s[ts] = row.ts
        s[lat] = row.lat
        s[lon] = row.lon
        s[bearing] = row.bearing
        s[speedMps] = row.speedMps
        s[odometerM] = row.odometerM
        s[descTripId] = row.descTripId
        s[descRouteId] = row.descRouteId
        s[descDirectionId] = row.descDirectionId?.toShort()
        s[descStartDate] = row.descStartDate
        s[descStartTimeSec] = row.descStartTimeSec
        s[descScheduleRelationship] = row.descScheduleRelationship?.toShort()
        s[currentStopSequence] = row.currentStopSequence
        s[currentStopId] = row.currentStopId
        s[currentStatus] = row.currentStatus?.toShort()
        s[occupancyStatus] = row.occupancyStatus?.toShort()
        s[congestionLevel] = row.congestionLevel?.toShort()
        s[matchStatus] = row.matchStatus
        s[matchedAt] = row.matchedAt
        s[createdAt] = row.createdAt
    }
}

@Repository
@Transactional
class AvlReportRowRepository {
    fun pendingMetricsByFeed(): List<PendingAvlMetrics> {
        val pendingCount = AvlReportTable.id.count()
        val oldestCreatedAt = AvlReportTable.createdAt.min()
        return AvlReportTable
            .select(AvlReportTable.feedCode, pendingCount, oldestCreatedAt)
            .where { AvlReportTable.matchStatus eq MatchStatus.PENDING }
            .groupBy(AvlReportTable.feedCode)
            .map { PendingAvlMetrics(it[AvlReportTable.feedCode], it[pendingCount], it[oldestCreatedAt]) }
    }

    fun findClaimBatch(limit: Int): List<AvlReportRow> =
        AvlReportTable
            .selectAll()
            .where { AvlReportTable.matchStatus eq MatchStatus.PENDING }
            .orderBy(
                AvlReportTable.feedCode to SortOrder.ASC,
                AvlReportTable.vehicleId to SortOrder.ASC,
                AvlReportTable.ts to SortOrder.ASC,
            ).limit(limit)
            .map(AvlReportTable::read)

    fun findClaimBatch(
        feedCode: String,
        limit: Int,
    ): List<AvlReportRow> =
        AvlReportTable
            .selectAll()
            .where { (AvlReportTable.matchStatus eq MatchStatus.PENDING) and (AvlReportTable.feedCode eq feedCode) }
            .orderBy(AvlReportTable.vehicleId to SortOrder.ASC, AvlReportTable.ts to SortOrder.ASC)
            .limit(limit)
            .map(AvlReportTable::read)

    fun latestTsByVehicle(feedCode: String): List<VehicleTsProjection> {
        val latest = AvlReportTable.ts.max()
        return AvlReportTable
            .select(AvlReportTable.vehicleId, latest)
            .where { AvlReportTable.feedCode eq feedCode }
            .groupBy(AvlReportTable.vehicleId)
            .map { VehicleTsProjection(it[AvlReportTable.vehicleId], it[latest]!!) }
    }

    fun findVehicleIds(feedCode: String): List<String> =
        AvlReportTable
            .select(AvlReportTable.vehicleId)
            .where { AvlReportTable.feedCode eq feedCode }
            .withDistinct()
            .orderBy(AvlReportTable.vehicleId)
            .map { it[AvlReportTable.vehicleId] }

    fun findByVehicle(
        feedCode: String,
        vehicleId: String,
        page: PageRequest,
    ): List<AvlReportRow> =
        AvlReportTable
            .selectAll()
            .where { (AvlReportTable.feedCode eq feedCode) and (AvlReportTable.vehicleId eq vehicleId) }
            .orderBy(AvlReportTable.ts, SortOrder.DESC)
            .limit(page.size)
            .offset(page.offset)
            .map(AvlReportTable::read)

    fun findByVehicleSince(
        feedCode: String,
        vehicleId: String,
        ts: Instant,
        page: PageRequest,
    ): List<AvlReportRow> =
        AvlReportTable
            .selectAll()
            .where {
                (AvlReportTable.feedCode eq feedCode) and (AvlReportTable.vehicleId eq vehicleId) and (AvlReportTable.ts greaterEq ts)
            }.orderBy(AvlReportTable.ts, SortOrder.DESC)
            .limit(page.size)
            .offset(page.offset)
            .map(AvlReportTable::read)

    /** One vehicle's reports in `[from, until]`, oldest first, plus the window's total row count. */
    fun findReportPage(
        feedCode: String,
        vehicleId: String,
        from: Instant,
        until: Instant,
        page: PageRequest,
    ): PageResult<AvlReportRow> {
        val inWindow: () -> Op<Boolean> = {
            (AvlReportTable.feedCode eq feedCode) and (AvlReportTable.vehicleId eq vehicleId) and
                (AvlReportTable.ts greaterEq from) and (AvlReportTable.ts lessEq until)
        }
        val items =
            AvlReportTable
                .selectAll()
                .where(inWindow)
                .orderBy(AvlReportTable.ts, SortOrder.ASC)
                .limit(page.size)
                .offset(page.offset)
                .map(AvlReportTable::read)
        val total = AvlReportTable.selectAll().where(inWindow).count()
        return PageResult(items, total)
    }

    /**
     * Recent reports for a whole batch of vehicles in one query — feeds the movement-trend heading
     * (see [eu.transittrack.avl.match.trendHeadingDeg]) without an N+1 per vehicle.
     */
    fun findTrail(
        feedCode: String,
        vehicleIds: Collection<String>,
        since: Instant,
    ): List<AvlReportRow> =
        AvlReportTable
            .selectAll()
            .where {
                (AvlReportTable.feedCode eq feedCode) and (AvlReportTable.vehicleId inList vehicleIds) and
                    (AvlReportTable.ts greaterEq since)
            }.orderBy(AvlReportTable.vehicleId to SortOrder.ASC, AvlReportTable.ts to SortOrder.DESC)
            .map(AvlReportTable::read)

    @Transactional
    fun markMatchedBatch(
        ids: List<Long>,
        status: MatchStatus,
        at: Instant,
    ) {
        AvlReportTable.update({ AvlReportTable.id inList ids }) {
            it[matchedAt] = at
            it[matchStatus] = status
        }
    }

    /** Age-based retention: deletes reports created before [cutoff] (their matches cascade). */
    fun deleteCreatedBefore(cutoff: Instant): Int = AvlReportTable.deleteWhere { createdAt less cutoff }

    fun findById(id: Long): AvlReportRow? =
        AvlReportTable
            .selectAll()
            .where { AvlReportTable.id eq id }
            .singleOrNull()
            ?.let(AvlReportTable::read)

    fun findAllById(ids: Iterable<Long>): List<AvlReportRow> =
        AvlReportTable
            .selectAll()
            .where { AvlReportTable.id inList ids.toList() }
            .map(AvlReportTable::read)

    fun findAll(): List<AvlReportRow> = AvlReportTable.selectAll().map(AvlReportTable::read)

    fun count(): Long = AvlReportTable.selectAll().count()

    /** Inserts when [row] has no id, else updates it in place; returns [row] with its id set. */
    fun save(row: AvlReportRow): AvlReportRow {
        val id = row.id
        if (id == null) {
            row.id = AvlReportTable.insert { AvlReportTable.bind(it, row) }[AvlReportTable.id]
        } else {
            AvlReportTable.update({ AvlReportTable.id eq id }) { AvlReportTable.bind(it, row) }
        }
        return row
    }

    fun deleteAll() {
        AvlReportTable.deleteAll()
    }
}
