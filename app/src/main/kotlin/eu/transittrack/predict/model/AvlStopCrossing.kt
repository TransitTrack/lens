package eu.transittrack.predict.model

import java.time.Instant

import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Sequence
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

import eu.transittrack.avl.model.AvlFeedTable
import eu.transittrack.db.RowTable

class AvlStopCrossing(
    var feedCode: String,
    var vehicleId: String,
    var tripRowId: Long,
    var tripPatternId: Long,
    var stopPathIndex: Int,
    var observedAt: Instant,
    /** Time since the vehicle passed the previous stop on this trip; `null` when that wasn't seen. */
    var observedTravelTimeSec: Double?,
    var serviceId: String?,
    var tripStartSec: Int?,
    var routeId: String?,
    var directionId: Int?,
    var revisionId: Long,
    var runStartedAt: Instant? = null,
    var reportGapSec: Int? = null,
    var id: Long? = null,
)

object AvlStopCrossingTable : RowTable<AvlStopCrossing>("avl_stop_crossing") {
    val id = long("id").autoIncrement(Sequence("avl_stop_crossing_seq", startWith = 1, incrementBy = 50))
    val feedCode =
        varchar("feed_code", 64).references(
            AvlFeedTable.code,
            onDelete = ReferenceOption.CASCADE,
            onUpdate = ReferenceOption.NO_ACTION,
            fkName = "fk_avl_stop_crossing_feed",
        )
    val vehicleId = varchar("vehicle_id", 255)
    val tripRowId = long("trip_row_id")
    val tripPatternId = long("trip_pattern_id")
    val stopPathIndex = integer("stop_path_index")
    val observedAt = timestampTz("observed_at")
    val observedTravelTimeSec = double("observed_travel_time_sec").nullable()
    val serviceId = text("service_id").nullable()
    val tripStartSec = integer("trip_start_sec").nullable()
    val routeId = text("route_id").nullable()
    val directionId = short("direction_id").nullable()
    val revisionId = long("revision_id")
    val runStartedAt = timestampTz("run_started_at").nullable()
    val reportGapSec = integer("report_gap_sec").nullable()

    override val primaryKey = PrimaryKey(id, name = "avl_stop_crossing_pkey")

    init {
        index("ix_avl_stop_crossing_feed_observed", false, feedCode, observedAt)
        index("ix_avl_stop_crossing_feed_revision_observed", false, feedCode, revisionId, observedAt)
        index("ix_avl_stop_crossing_observed", false, observedAt)
        index("ix_avl_stop_crossing_pattern_stop_observed", false, tripPatternId, stopPathIndex, observedAt)
        index("ix_avl_stop_crossing_trip_observed", false, tripRowId, observedAt)
        // observed_at DESC in the database; Exposed indexes carry no sort order.
        index("ix_avl_stop_crossing_vehicle_trip_stop", false, feedCode, vehicleId, tripRowId, stopPathIndex, observedAt)
    }

    override fun read(row: ResultRow) =
        AvlStopCrossing(
            feedCode = row[feedCode],
            vehicleId = row[vehicleId],
            tripRowId = row[tripRowId],
            tripPatternId = row[tripPatternId],
            stopPathIndex = row[stopPathIndex],
            observedAt = row[observedAt],
            observedTravelTimeSec = row[observedTravelTimeSec],
            serviceId = row[serviceId],
            tripStartSec = row[tripStartSec],
            routeId = row[routeId],
            directionId = row[directionId]?.toInt(),
            revisionId = row[revisionId],
            runStartedAt = row[runStartedAt],
            reportGapSec = row[reportGapSec],
            id = row[id],
        )

    override fun bind(
        s: UpdateBuilder<*>,
        row: AvlStopCrossing,
    ) {
        s[feedCode] = row.feedCode
        s[vehicleId] = row.vehicleId
        s[tripRowId] = row.tripRowId
        s[tripPatternId] = row.tripPatternId
        s[stopPathIndex] = row.stopPathIndex
        s[observedAt] = row.observedAt
        s[observedTravelTimeSec] = row.observedTravelTimeSec
        s[serviceId] = row.serviceId
        s[tripStartSec] = row.tripStartSec
        s[routeId] = row.routeId
        s[directionId] = row.directionId?.toShort()
        s[revisionId] = row.revisionId
        s[runStartedAt] = row.runStartedAt
        s[reportGapSec] = row.reportGapSec
    }
}

@Repository
@Transactional
class AvlStopCrossingRepository {
    /**
     * Bounded projection for the optimization analysis pipeline
     * ([eu.transittrack.schedule.optimize.OptimizationAnalysisPipeline]): filters at the query
     * level by feed, the run's frozen revision, the requested observed-date range, and the
     * optional service/route/direction/scheduled-departure-window filters, so a run never loads
     * crossings outside its own scope into memory. A null filter is left out of the predicate.
     */
    fun findForAnalysis(
        feedCodes: Collection<String>,
        revisionId: Long,
        from: Instant,
        to: Instant,
        serviceId: String?,
        routeId: String?,
        directionId: Int?,
        windowFromSec: Int?,
        windowToSec: Int?,
    ): List<AvlStopCrossing> =
        AvlStopCrossingTable
            .selectAll()
            .where {
                listOfNotNull<Op<Boolean>>(
                    AvlStopCrossingTable.feedCode inList feedCodes,
                    AvlStopCrossingTable.revisionId eq revisionId,
                    AvlStopCrossingTable.observedAt greaterEq from,
                    AvlStopCrossingTable.observedAt less to,
                    AvlStopCrossingTable.observedTravelTimeSec.isNotNull(),
                    serviceId?.let { AvlStopCrossingTable.serviceId eq it },
                    routeId?.let { AvlStopCrossingTable.routeId eq it },
                    directionId?.let { AvlStopCrossingTable.directionId eq it.toShort() },
                    windowFromSec?.let { AvlStopCrossingTable.tripStartSec greaterEq it },
                    windowToSec?.let { AvlStopCrossingTable.tripStartSec lessEq it },
                ).reduce { a, b -> a and b }
            }.map(AvlStopCrossingTable::read)

    /** This vehicle's most recent pass of one stop on one trip, no earlier than [since]. */
    fun findLastPass(
        feedCode: String,
        vehicleId: String,
        tripRowId: Long,
        stopPathIndex: Int,
        since: Instant,
    ): AvlStopCrossing? =
        AvlStopCrossingTable
            .selectAll()
            .where {
                (AvlStopCrossingTable.feedCode eq feedCode) and (AvlStopCrossingTable.vehicleId eq vehicleId) and
                    (AvlStopCrossingTable.tripRowId eq tripRowId) and (AvlStopCrossingTable.stopPathIndex eq stopPathIndex) and
                    (AvlStopCrossingTable.observedAt greaterEq since)
            }.orderBy(AvlStopCrossingTable.observedAt, SortOrder.DESC)
            .limit(1)
            .singleOrNull()
            ?.let(AvlStopCrossingTable::read)

    /**
     * Newest [limit] plausible crossings of [feedCode] observed at or after [since]: travel time
     * known and at most [maxTravelSec], report gap unknown or at most [maxReportGapSec]. Only the
     * columns training uses are read, and the rows are streamed ([TRAINING_FETCH_SIZE] at a time)
     * into primitive arrays, so neither the driver nor the result holds a full row per crossing.
     */
    fun training(
        feedCode: String,
        since: Instant,
        maxReportGapSec: Int,
        maxTravelSec: Int,
        limit: Int,
    ): TrainingCrossings {
        val t = AvlStopCrossingTable
        val out = TrainingCrossings.Builder(initialCapacity = limit.coerceAtMost(TRAINING_FETCH_SIZE))
        t
            .select(
                t.tripPatternId, t.revisionId, t.stopPathIndex, t.observedAt, t.observedTravelTimeSec, t.tripStartSec, t.routeId,
                t.directionId,
            ).where {
                (t.feedCode eq feedCode) and (t.observedAt greaterEq since) and t.observedTravelTimeSec.isNotNull() and
                    (t.observedTravelTimeSec lessEq maxTravelSec.toDouble()) and
                    (t.reportGapSec.isNull() or (t.reportGapSec lessEq maxReportGapSec))
            }.orderBy(t.observedAt, SortOrder.DESC)
            .limit(limit)
            // Inside the transaction (auto-commit off) a fetch size makes the PostgreSQL driver use a
            // cursor instead of reading the whole result into memory first.
            .fetchSize(TRAINING_FETCH_SIZE)
            .forEach { row ->
                out.add(
                    tripPatternId = row[t.tripPatternId],
                    revisionId = row[t.revisionId],
                    stopPathIndex = row[t.stopPathIndex],
                    observedAt = row[t.observedAt],
                    travelSec = row[t.observedTravelTimeSec]!!,
                    tripStartSec = row[t.tripStartSec],
                    directionId = row[t.directionId]?.toInt(),
                    routeId = row[t.routeId],
                )
            }
        return out.build()
    }

    fun findAll(): List<AvlStopCrossing> = AvlStopCrossingTable.selectAll().map(AvlStopCrossingTable::read)

    fun count(): Long = AvlStopCrossingTable.selectAll().count()

    /** Inserts when [row] has no id, else updates it in place; returns [row] with its id set. */
    fun save(row: AvlStopCrossing): AvlStopCrossing {
        val id = row.id
        if (id == null) {
            row.id = AvlStopCrossingTable.insert { AvlStopCrossingTable.bind(it, row) }[AvlStopCrossingTable.id]
        } else {
            AvlStopCrossingTable.update({ AvlStopCrossingTable.id eq id }) { AvlStopCrossingTable.bind(it, row) }
        }
        return row
    }

    fun saveAll(rows: Iterable<AvlStopCrossing>): List<AvlStopCrossing> = rows.map(::save)

    fun deleteAll() {
        AvlStopCrossingTable.deleteAll()
    }

    private companion object {
        const val TRAINING_FETCH_SIZE = 5_000
    }
}
