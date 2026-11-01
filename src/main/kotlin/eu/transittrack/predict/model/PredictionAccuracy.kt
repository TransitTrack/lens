package eu.transittrack.predict.model

import java.time.Instant

import org.jetbrains.exposed.v1.core.IColumnType
import org.jetbrains.exposed.v1.core.IntegerColumnType
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Sequence
import org.jetbrains.exposed.v1.core.ShortColumnType
import org.jetbrains.exposed.v1.core.TextColumnType
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.statements.StatementType
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.update
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

import eu.transittrack.avl.model.AvlFeedTable
import eu.transittrack.db.RowTable
import eu.transittrack.predict.PredictionAlgorithm
import eu.transittrack.predict.learn.DayType
import eu.transittrack.predict.learn.TimeBand

class PredictionAccuracy(
    var feedCode: String,
    var vehicleId: String,
    var tripRowId: Long,
    var stopPathIndex: Int,
    var algorithm: PredictionAlgorithm,
    var predictedTs: Instant?,
    var actualTs: Instant,
    var errorSec: Int?,
    var absErrorSec: Int?,
    var createdAt: Instant,
    /** Actual lead-time bin's upper bound; null on early legacy rows. */
    var horizonSec: Int? = null,
    /** Source AVL report time; actual issuance is stored separately in [producedAt]. */
    var predictedAt: Instant? = null,
    var producedAt: Instant? = null,
    var actualLeadSec: Int? = null,
    var confidenceSec: Int? = null,
    var routeId: String? = null,
    var directionId: Int? = null,
    var dayType: DayType? = null,
    var timeBand: TimeBand? = null,
    var id: Long? = null,
)

/** Accuracy of one algorithm at one lead-time horizon, aggregated over a time window. */
data class PredictionAccuracyAggregate(
    val algorithmOrdinal: Int,
    val horizonSec: Int?,
    val eligibleCount: Long,
    val sampleCount: Long,
    val pairedSampleCount: Long,
    val meanErrorSec: Double?,
    val meanAbsErrorSec: Double?,
    val p90AbsErrorSec: Double?,
    val p95AbsErrorSec: Double?,
    val confidenceCoverage: Double?,
    val confidenceSampleCount: Long,
)

object PredictionAccuracyTable : RowTable<PredictionAccuracy>("prediction_accuracy") {
    val id = long("id").autoIncrement(Sequence("prediction_accuracy_seq", startWith = 1, incrementBy = 50))
    val feedCode =
        varchar("feed_code", 64).references(
            AvlFeedTable.code,
            onDelete = ReferenceOption.CASCADE,
            onUpdate = ReferenceOption.NO_ACTION,
            fkName = "fk_prediction_accuracy_feed",
        )
    val vehicleId = varchar("vehicle_id", 255)
    val tripRowId = long("trip_row_id")
    val stopPathIndex = integer("stop_path_index")
    val algorithm = shortOrdinal<PredictionAlgorithm>("algorithm")
    val predictedTs = timestampTz("predicted_ts").nullable()
    val actualTs = timestampTz("actual_ts")
    val errorSec = integer("error_sec").nullable()
    val absErrorSec = integer("abs_error_sec").nullable()
    val createdAt = timestampTz("created_at").defaultNow()
    val horizonSec = integer("horizon_sec").nullable()
    val predictedAt = timestampTz("predicted_at").nullable()
    val producedAt = timestampTz("produced_at").nullable()
    val actualLeadSec = integer("actual_lead_sec").nullable()
    val confidenceSec = integer("confidence_sec").nullable()
    val routeId = varchar("route_id", 255).nullable()
    val directionId = short("direction_id").nullable()
    val dayType = shortOrdinal<DayType>("day_type").nullable()
    val timeBand = shortOrdinal<TimeBand>("time_band").nullable()

    override val primaryKey = PrimaryKey(id, name = "prediction_accuracy_pkey")

    init {
        index("ix_prediction_accuracy_actual", false, feedCode, actualTs)
        index("ix_prediction_accuracy_created", false, createdAt)
        index("ix_prediction_accuracy_feed_algo_created", false, feedCode, algorithm, createdAt)
    }

    override fun read(row: ResultRow) =
        PredictionAccuracy(
            feedCode = row[feedCode],
            vehicleId = row[vehicleId],
            tripRowId = row[tripRowId],
            stopPathIndex = row[stopPathIndex],
            algorithm = row[algorithm],
            predictedTs = row[predictedTs],
            actualTs = row[actualTs],
            errorSec = row[errorSec],
            absErrorSec = row[absErrorSec],
            createdAt = row[createdAt],
            horizonSec = row[horizonSec],
            predictedAt = row[predictedAt],
            producedAt = row[producedAt],
            actualLeadSec = row[actualLeadSec],
            confidenceSec = row[confidenceSec],
            routeId = row[routeId],
            directionId = row[directionId]?.toInt(),
            dayType = row[dayType],
            timeBand = row[timeBand],
            id = row[id],
        )

    override fun bind(
        s: UpdateBuilder<*>,
        row: PredictionAccuracy,
    ) {
        s[feedCode] = row.feedCode
        s[vehicleId] = row.vehicleId
        s[tripRowId] = row.tripRowId
        s[stopPathIndex] = row.stopPathIndex
        s[algorithm] = row.algorithm
        s[predictedTs] = row.predictedTs
        s[actualTs] = row.actualTs
        s[errorSec] = row.errorSec
        s[absErrorSec] = row.absErrorSec
        s[createdAt] = row.createdAt
        s[horizonSec] = row.horizonSec
        s[predictedAt] = row.predictedAt
        s[producedAt] = row.producedAt
        s[actualLeadSec] = row.actualLeadSec
        s[confidenceSec] = row.confidenceSec
        s[routeId] = row.routeId
        s[directionId] = row.directionId?.toShort()
        s[dayType] = row.dayType
        s[timeBand] = row.timeBand
    }
}

@Repository
@Transactional
class PredictionAccuracyRepository {
    /**
     * Legacy ETA-selected checkpoints are excluded; only shared, actual-lead snapshots are comparable.
     * Raw SQL ([AGGREGATE_ACCURACY_SQL]); [algorithms] binds one placeholder per value, as the Spring
     * Data native query expanded it. No [algorithms] means nothing to aggregate (and `in ()` isn't
     * valid SQL), so nothing is queried.
     */
    fun aggregateAccuracy(
        feedCode: String,
        algorithms: Collection<Short>,
        algorithmCount: Int,
        since: Instant,
        routeId: String?,
        directionId: Int?,
        dayType: Short?,
        timeBand: Short?,
    ): List<PredictionAccuracyAggregate> {
        if (algorithms.isEmpty()) return emptyList()
        val text = TextColumnType()
        val int = IntegerColumnType()
        val short = ShortColumnType()
        val args =
            buildList<Pair<IColumnType<*>, Any?>> {
                add(text to feedCode)
                algorithms.forEach { add(short to it) }
                add(PredictionAccuracyTable.actualTs.columnType to since)
                add(text to routeId)
                add(text to routeId)
                add(int to directionId)
                add(int to directionId)
                add(short to dayType)
                add(short to dayType)
                add(short to timeBand)
                add(short to timeBand)
                // Five FILTERs and the cohort's eligible_algorithms check.
                repeat(6) { add(int to algorithmCount) }
            }
        val sql = AGGREGATE_ACCURACY_SQL.replace(ALGORITHMS, algorithms.joinToString { "?" })
        return TransactionManager.current().exec(sql, args, StatementType.SELECT) { rs ->
            buildList {
                while (rs.next()) {
                    add(
                        PredictionAccuracyAggregate(
                            algorithmOrdinal = rs.getInt("algorithmOrdinal"),
                            horizonSec = rs.getObject("horizonSec")?.let { (it as Number).toInt() },
                            eligibleCount = rs.getLong("eligibleCount"),
                            sampleCount = rs.getLong("sampleCount"),
                            pairedSampleCount = rs.getLong("pairedSampleCount"),
                            meanErrorSec = rs.getObject("meanErrorSec")?.let { (it as Number).toDouble() },
                            meanAbsErrorSec = rs.getObject("meanAbsErrorSec")?.let { (it as Number).toDouble() },
                            p90AbsErrorSec = rs.getObject("p90AbsErrorSec")?.let { (it as Number).toDouble() },
                            p95AbsErrorSec = rs.getObject("p95AbsErrorSec")?.let { (it as Number).toDouble() },
                            confidenceCoverage = rs.getObject("confidenceCoverage")?.let { (it as Number).toDouble() },
                            confidenceSampleCount = rs.getLong("confidenceSampleCount"),
                        ),
                    )
                }
            }
        } ?: emptyList()
    }

    fun findRecentByFeedAndAlgorithm(
        feedCode: String,
        algorithm: PredictionAlgorithm,
        since: Instant,
    ): List<PredictionAccuracy> =
        PredictionAccuracyTable
            .selectAll()
            .where {
                (PredictionAccuracyTable.feedCode eq feedCode) and (PredictionAccuracyTable.algorithm eq algorithm) and
                    (PredictionAccuracyTable.createdAt greater since)
            }.map(PredictionAccuracyTable::read)

    /**
     * Bounded evidence lookup for the optimization analysis pipeline
     * ([eu.transittrack.schedule.optimize.OptimizationAnalysisPipeline]): scoped to one feed, one
     * stop-path index, a set of trip rows, and the run's observed date range — never all feeds or
     * algorithms.
     */
    fun findForTargets(
        feedCodes: Collection<String>,
        tripRowIds: Collection<Long>,
        stopPathIndex: Int,
        from: Instant,
        to: Instant,
    ): List<PredictionAccuracy> =
        PredictionAccuracyTable
            .selectAll()
            .where {
                (PredictionAccuracyTable.feedCode inList feedCodes) and (PredictionAccuracyTable.tripRowId inList tripRowIds) and
                    (PredictionAccuracyTable.stopPathIndex eq stopPathIndex) and (PredictionAccuracyTable.createdAt greaterEq from) and
                    (PredictionAccuracyTable.createdAt less to) and PredictionAccuracyTable.errorSec.isNotNull()
            }.map(PredictionAccuracyTable::read)

    fun findAll(): List<PredictionAccuracy> = PredictionAccuracyTable.selectAll().map(PredictionAccuracyTable::read)

    fun count(): Long = PredictionAccuracyTable.selectAll().count()

    /** Inserts when [row] has no id, else updates it in place; returns [row] with its id set. */
    fun save(row: PredictionAccuracy): PredictionAccuracy {
        val id = row.id
        if (id == null) {
            row.id = PredictionAccuracyTable.insert { PredictionAccuracyTable.bind(it, row) }[PredictionAccuracyTable.id]
        } else {
            PredictionAccuracyTable.update({ PredictionAccuracyTable.id eq id }) { PredictionAccuracyTable.bind(it, row) }
        }
        return row
    }

    fun saveAll(rows: Iterable<PredictionAccuracy>): List<PredictionAccuracy> = rows.map(::save)

    fun deleteById(id: Long) {
        PredictionAccuracyTable.deleteWhere { PredictionAccuracyTable.id eq id }
    }

    fun deleteAllById(ids: Iterable<Long>) {
        val list = ids.toList()
        if (list.isEmpty()) return
        PredictionAccuracyTable.deleteWhere { PredictionAccuracyTable.id inList list }
    }

    fun deleteAll() {
        PredictionAccuracyTable.deleteAll()
    }

    private companion object {
        /** Replaced by one `?` per algorithm. */
        const val ALGORITHMS = ":algorithms"

        // Raw SQL: CTEs, aggregate FILTER clauses, percentile_cont ... WITHIN GROUP and GROUPING SETS have no Exposed DSL form.
        const val AGGREGATE_ACCURACY_SQL = """
            with snapshots as (
                select * from prediction_accuracy
                where feed_code = ? and algorithm in ($ALGORITHMS) and actual_ts > ?
                  and horizon_sec is not null and actual_lead_sec is not null and predicted_at is not null and produced_at is not null
                  and (cast(? as text) is null or route_id = cast(? as text))
                  and (cast(? as smallint) is null or direction_id = cast(? as smallint))
                  and (cast(? as smallint) is null or day_type = cast(? as smallint))
                  and (cast(? as smallint) is null or time_band = cast(? as smallint))
            ), cohorts as (
                select vehicle_id, trip_row_id, stop_path_index, horizon_sec, predicted_at, produced_at, actual_ts,
                       count(distinct algorithm) as eligible_algorithms,
                       count(distinct algorithm) filter (where error_sec is not null) as available_algorithms
                from snapshots
                group by vehicle_id, trip_row_id, stop_path_index, horizon_sec, predicted_at, produced_at, actual_ts
            )
            select s.algorithm as "algorithmOrdinal", s.horizon_sec as "horizonSec",
                   count(*) as "eligibleCount", count(s.error_sec) as "sampleCount",
                   count(*) filter (where c.available_algorithms = ?) as "pairedSampleCount",
                   avg(s.error_sec) filter (where c.available_algorithms = ?) as "meanErrorSec",
                   avg(s.abs_error_sec) filter (where c.available_algorithms = ?) as "meanAbsErrorSec",
                   percentile_cont(0.90) within group (order by s.abs_error_sec)
                       filter (where c.available_algorithms = ?) as "p90AbsErrorSec",
                   percentile_cont(0.95) within group (order by s.abs_error_sec)
                       filter (where c.available_algorithms = ?) as "p95AbsErrorSec",
                   avg(case when s.confidence_sec is null or s.error_sec is null then null
                            when s.abs_error_sec <= s.confidence_sec then 1.0 else 0.0 end) as "confidenceCoverage",
                   count(*) filter (where s.confidence_sec is not null and s.error_sec is not null) as "confidenceSampleCount"
            from snapshots s join cohorts c using
                (vehicle_id, trip_row_id, stop_path_index, horizon_sec, predicted_at, produced_at, actual_ts)
            where c.eligible_algorithms = ?
            group by grouping sets ((s.algorithm), (s.algorithm, s.horizon_sec))
            order by s.algorithm, s.horizon_sec nulls first
        """
    }
}
