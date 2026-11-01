package eu.transittrack.predict

import java.time.Duration
import java.time.Instant
import java.time.LocalDate

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.CustomFunction
import org.jetbrains.exposed.v1.core.GreaterEqOp
import org.jetbrains.exposed.v1.core.IntegerColumnType
import org.jetbrains.exposed.v1.core.IsDistinctFromOp
import org.jetbrains.exposed.v1.core.IsNotDistinctFromOp
import org.jetbrains.exposed.v1.core.NeqOp
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.QueryParameter
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.TextColumnType
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.andIfNotNull
import org.jetbrains.exposed.v1.core.case
import org.jetbrains.exposed.v1.core.coalesce
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.functions.math.AbsFunction
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.intLiteral
import org.jetbrains.exposed.v1.core.intParam
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.core.nextLongVal
import org.jetbrains.exposed.v1.core.notExists
import org.jetbrains.exposed.v1.core.notInList
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.core.statements.buildStatement
import org.jetbrains.exposed.v1.core.statements.insertValue
import org.jetbrains.exposed.v1.core.stringParam
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.upsert
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

import eu.transittrack.db.EpochSecondsBetween
import eu.transittrack.db.InsertSelectOnConflictDoNothing
import eu.transittrack.db.batchInsertWithoutResults
import eu.transittrack.db.batchUpsertWithoutResults
import eu.transittrack.db.execBatch
import eu.transittrack.predict.learn.KalmanState
import eu.transittrack.predict.learn.LearningKey
import eu.transittrack.predict.learn.RunningAverageState
import eu.transittrack.predict.learn.TravelTimeBucket
import eu.transittrack.predict.model.AvlStopCrossing
import eu.transittrack.predict.model.AvlStopCrossingTable
import eu.transittrack.predict.model.KalmanTravelTimeStateTable
import eu.transittrack.predict.model.LearnerBucketColumns
import eu.transittrack.predict.model.PredictionAccuracy
import eu.transittrack.predict.model.PredictionAccuracyTable
import eu.transittrack.predict.model.PredictionCheckpoint
import eu.transittrack.predict.model.PredictionCheckpointTable
import eu.transittrack.predict.model.PredictionFrequencyRunTable
import eu.transittrack.predict.model.TravelTimeObservationTable
import eu.transittrack.predict.model.VehiclePredictionTable

data class VehiclePredictionUpsert(
    val feedCode: String,
    val vehicleId: String,
    val tripRowId: Long,
    val blockPk: Long?,
    val tripPatternId: Long,
    val stopPathIndex: Int,
    val algorithm: PredictionAlgorithm,
    val predictedArrivalTs: Instant?,
    val predictedDepartureTs: Instant?,
    val actualArrivalTs: Instant?,
    val actualDepartureTs: Instant?,
    val confidenceSec: Int?,
    val computedAt: Instant,
    val sourceReportTs: Instant = computedAt,
    val serviceDate: LocalDate? = null,
)

/** One learner row of a [LearningKey]: a stop path's time in one day-type/time-band bucket. */
data class LearnerBucket(
    val stopPathIndex: Int,
    val bucket: TravelTimeBucket,
)

data class CheckpointCandidate(
    val feedCode: String,
    val vehicleId: String,
    val tripRowId: Long,
    val stopPathIndex: Int,
    val algorithm: PredictionAlgorithm,
    val predictedTs: Instant?,
    val computedAt: Instant,
    val sampleSlot: Instant,
    val confidenceSec: Int? = null,
    val serviceDate: LocalDate? = null,
    val producedAt: Instant = computedAt,
)

data class FrequencyRunState(
    val serviceDate: LocalDate,
    val startedAt: Instant,
    val lastReportTs: Instant,
    val startTimeSec: Int?,
    val lastDistanceAlongTripM: Double? = null,
)

/**
 * Persistence for the prediction pipeline. `travel_time_observation` and `kalman_travel_time_state`
 * are `INSERT ... ON CONFLICT` upserts keyed on
 * `(gtfs_feed_code, pattern_key, stop_path_index, day_type, time_band)`;
 * `vehicle_prediction` is an upsert keyed on
 * `(feed_code, vehicle_id, trip_row_id, stop_path_index, algorithm)`. `prediction_accuracy` and
 * `avl_stop_crossing` are plain appends whose generated ids are never read back.
 *
 * Writes that come in sets (a report's predictions, its stop passes, their learner rows) go out as
 * one JDBC batch per set rather than one round trip per row.
 *
 * Every read and write runs in the transaction [inTransaction] opened rather than its own:
 * [PredictionService.onMatched] can generate dozens of these per matched report (one per horizon
 * stop per algorithm, plus crossings/learning), and committing each individually made that commit's
 * fsync the dominant cost of the whole prediction pipeline. Reads go through the same transaction
 * too, so they see its own writes and hold no second pooled connection. Callers open one transaction
 * per `onMatched()` call via [inTransaction] and call the other methods inside its block.
 */
@Component
@Transactional
class PredictionWriter {
    /**
     * Runs [block] in a transaction of its own, committed when [block] returns and rolled back if it
     * throws anything: never part of a caller's transaction (`REQUIRES_NEW`), so its writes stand or
     * fall alone, and the row and advisory locks taken inside it are held until it ends. Every method
     * of this writer called inside [block] (through the Spring proxy, as any caller holding this bean
     * does) joins that transaction (the class-level `@Transactional` is `REQUIRED`).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = [Throwable::class])
    fun inTransaction(block: () -> Unit) = block()

    fun frequencyRun(
        feedCode: String,
        vehicleId: String,
        tripRowId: Long,
    ): FrequencyRunState? {
        lockKey("transittrack.predict.frequency:$feedCode", "${vehicleId.length}:$vehicleId:$tripRowId")
        return PredictionFrequencyRunTable
            .selectAll()
            .where {
                (PredictionFrequencyRunTable.feedCode eq feedCode) and (PredictionFrequencyRunTable.vehicleId eq vehicleId) and
                    (PredictionFrequencyRunTable.tripRowId eq tripRowId)
            }.forUpdate()
            .singleOrNull()
            ?.let(PredictionFrequencyRunTable::read)
    }

    /** Stores [state] unless the stored run already saw a later report. */
    fun saveFrequencyRun(
        feedCode: String,
        vehicleId: String,
        tripRowId: Long,
        state: FrequencyRunState,
    ) {
        val t = PredictionFrequencyRunTable
        t.upsert(t.feedCode, t.vehicleId, t.tripRowId, where = { t.lastReportTs lessEq insertValue(t.lastReportTs) }) {
            it[t.feedCode] = feedCode
            it[t.vehicleId] = vehicleId
            it[t.tripRowId] = tripRowId
            t.bind(it, state)
        }
    }

    /**
     * When this vehicle last passed each stop of a trip, no earlier than [since] (an earlier pass
     * belongs to the same trip row on another service day).
     */
    fun lastPasses(
        feedCode: String,
        vehicleId: String,
        tripRowId: Long,
        since: Instant,
    ): Map<Int, Instant> = passObservations(feedCode, vehicleId, tripRowId, since).associate { it.stopPathIndex to it.observedAt }

    fun passObservations(
        feedCode: String,
        vehicleId: String,
        tripRowId: Long,
        since: Instant,
        until: Instant? = null,
    ): List<AvlStopCrossing> {
        val c = AvlStopCrossingTable
        return c
            .selectAll()
            .where {
                ((c.feedCode eq feedCode) and (c.vehicleId eq vehicleId) and (c.tripRowId eq tripRowId) and (c.observedAt greaterEq since))
                    .andIfNotNull(until?.let { c.observedAt lessEq it })
            }.orderBy(c.observedAt)
            .map(c::read)
            .let { rows ->
                val runStartedAt = rows.lastOrNull()?.runStartedAt
                if (runStartedAt == null) rows else rows.filter { !it.observedAt.isBefore(runStartedAt) }
            }
    }

    /**
     * Reads the learner rows of [buckets] and locks them until the transaction ends: two AVL feeds
     * sharing a GTFS feed learn into the same rows, and an unlocked read-modify-write would lose one
     * of two concurrent samples. A transaction-scoped logical lock also protects absent rows during
     * initialization. Rows are locked in key order (stop path, then fine to coarse bucket),
     * and every transaction locks all its observation rows before its Kalman state rows, so concurrent
     * transactions can't deadlock. A bucket with no row yet is missing from the result.
     */
    fun lockObservations(
        key: LearningKey,
        buckets: Collection<LearnerBucket>,
    ): Map<LearnerBucket, RunningAverageState> =
        lockBucketRows(TravelTimeObservationTable, key, buckets) {
            RunningAverageState(it[TravelTimeObservationTable.sampleCount], it[TravelTimeObservationTable.meanSec])
        }

    /** See [lockObservations]. */
    fun lockKalmanStates(
        key: LearningKey,
        buckets: Collection<LearnerBucket>,
        observedAt: Instant? = null,
        varianceGrowthPerHour: Double = 0.0,
    ): Map<LearnerBucket, KalmanState> =
        lockBucketRows(KalmanTravelTimeStateTable, key, buckets) { row ->
            val ageHours = observedAt?.let {
                Duration.between(row[KalmanTravelTimeStateTable.updatedAt], it).seconds.coerceAtLeast(0) / 3600.0
            } ?: 0.0
            KalmanState(
                row[KalmanTravelTimeStateTable.estimateSec],
                row[KalmanTravelTimeStateTable.errorVariance] + ageHours * varianceGrowthPerHour,
                row[KalmanTravelTimeStateTable.sampleCount],
            )
        }

    private fun <T : Any, L> lockBucketRows(
        table: L,
        key: LearningKey,
        buckets: Collection<LearnerBucket>,
        read: (ResultRow) -> T,
    ): Map<LearnerBucket, T> where L : Table, L : LearnerBucketColumns {
        val keys = buckets.distinct()
        if (keys.isEmpty()) return emptyMap()
        lockKey("transittrack.predict.learn:${key.gtfsFeedCode}", key.patternKey)
        val bucketKeys = keys.map { Triple(it.stopPathIndex, it.bucket.dayType, it.bucket.timeBand) }
        return table
            .selectAll()
            .where {
                (table.gtfsFeedCode eq key.gtfsFeedCode) and (table.patternKey eq key.patternKey) and
                    (Triple(table.stopPathIndex, table.dayType, table.timeBand) inList bucketKeys)
            }.orderBy(table.stopPathIndex to SortOrder.ASC, table.dayType to SortOrder.ASC, table.timeBand to SortOrder.ASC)
            .forUpdate()
            .associate { row ->
                LearnerBucket(row[table.stopPathIndex], TravelTimeBucket(row[table.dayType], row[table.timeBand])) to read(row)
            }
    }

    fun upsertObservations(
        key: LearningKey,
        rows: Map<LearnerBucket, RunningAverageState>,
        at: Instant,
    ) {
        if (rows.isEmpty()) return
        val t = TravelTimeObservationTable
        t.batchUpsertWithoutResults(
            rows.entries.sortedWith(BUCKET_ORDER),
            t.gtfsFeedCode, t.patternKey, t.stopPathIndex, t.dayType, t.timeBand,
            onUpdate = {
                it[t.sampleCount] = insertValue(t.sampleCount)
                it[t.meanSec] = insertValue(t.meanSec)
                it[t.updatedAt] = greatestOfStoredAndInserted(t.updatedAt)
            },
        ) { (b, state) ->
            setBucketKey(t, key, b)
            this[t.sampleCount] = state.sampleCount
            this[t.meanSec] = state.meanSec
            this[t.updatedAt] = at
        }
    }

    fun upsertKalmanStates(
        key: LearningKey,
        rows: Map<LearnerBucket, KalmanState>,
        at: Instant,
    ) {
        if (rows.isEmpty()) return
        val t = KalmanTravelTimeStateTable
        t.batchUpsertWithoutResults(
            rows.entries.sortedWith(BUCKET_ORDER),
            t.gtfsFeedCode, t.patternKey, t.stopPathIndex, t.dayType, t.timeBand,
            onUpdate = {
                it[t.estimateSec] = insertValue(t.estimateSec)
                it[t.errorVariance] = insertValue(t.errorVariance)
                it[t.sampleCount] = insertValue(t.sampleCount)
                it[t.updatedAt] = greatestOfStoredAndInserted(t.updatedAt)
            },
        ) { (b, state) ->
            setBucketKey(t, key, b)
            this[t.estimateSec] = state.estimateSec
            this[t.errorVariance] = state.errorVariance
            this[t.sampleCount] = state.sampleCount
            this[t.updatedAt] = at
        }
    }

    /**
     * Writes a vehicle's predictions, leaving a stored row as it is when rewriting it would change
     * nothing that matters - so a vehicle reporting every few seconds doesn't rewrite its whole
     * horizon each time:
     * - a row the vehicle already passed (with an actual arrival/departure) is kept, so it can't be
     *   overwritten with a prediction for a stop that's behind the vehicle;
     * - an open row is rewritten once its predicted arrival or departure moves by at least
     *   [minChangeSec], or once it was computed before [refreshBefore]. Readers use the source
     *   report's age, not processing time, to judge liveness.
     *
     * A row computed before [staleBefore] belongs to an earlier run of the trip and is replaced
     * whole, actuals included. A different service date also starts fresh. An older source report
     * never overwrites a newer one.
     */
    fun upsertPredictions(
        rows: List<VehiclePredictionUpsert>,
        staleBefore: Instant,
        refreshBefore: Instant,
        minChangeSec: Int,
    ) {
        if (rows.isEmpty()) return
        val t = VehiclePredictionTable

        fun movedBy(column: Column<Instant?>) =
            GreaterEqOp(AbsFunction(EpochSecondsBetween(column, insertValue(column))), intParam(minChangeSec))

        fun appearedOrVanished(column: Column<Instant?>) = NeqOp(column.isNull(), insertValue(column).isNull())
        t.batchUpsertWithoutResults(
            rows,
            t.feedCode, t.vehicleId, t.tripRowId, t.stopPathIndex, t.algorithm,
            onUpdate = {
                it[t.blockPk] = insertValue(t.blockPk)
                it[t.tripPatternId] = insertValue(t.tripPatternId)
                it[t.predictedArrivalTs] = insertValue(t.predictedArrivalTs)
                it[t.predictedDepartureTs] = insertValue(t.predictedDepartureTs)
                it[t.actualArrivalTs] = insertValue(t.actualArrivalTs)
                it[t.actualDepartureTs] = insertValue(t.actualDepartureTs)
                it[t.actualReportGapSec] = null
                it[t.confidenceSec] = insertValue(t.confidenceSec)
                it[t.computedAt] = insertValue(t.computedAt)
                it[t.sourceReportTs] = insertValue(t.sourceReportTs)
                it[t.serviceDate] = insertValue(t.serviceDate)
            },
            where = {
                (t.sourceReportTs.isNull() or (t.sourceReportTs lessEq insertValue(t.sourceReportTs))) and
                    (
                        IsDistinctFromOp(t.serviceDate, insertValue(t.serviceDate)) or
                            (t.computedAt less staleBefore) or
                            (
                                t.actualArrivalTs.isNull() and t.actualDepartureTs.isNull() and
                                    (
                                        (t.computedAt less refreshBefore) or
                                            IsDistinctFromOp(t.blockPk, insertValue(t.blockPk)) or
                                            (t.tripPatternId neq insertValue(t.tripPatternId)) or
                                            appearedOrVanished(t.predictedArrivalTs) or
                                            appearedOrVanished(t.predictedDepartureTs) or
                                            movedBy(t.predictedArrivalTs) or
                                            movedBy(t.predictedDepartureTs)
                                    )
                            )
                    )
            },
        ) { p ->
            this[t.feedCode] = p.feedCode
            this[t.vehicleId] = p.vehicleId
            this[t.tripRowId] = p.tripRowId
            this[t.blockPk] = p.blockPk
            this[t.tripPatternId] = p.tripPatternId
            this[t.stopPathIndex] = p.stopPathIndex
            this[t.algorithm] = p.algorithm
            this[t.predictedArrivalTs] = p.predictedArrivalTs
            this[t.predictedDepartureTs] = p.predictedDepartureTs
            this[t.actualArrivalTs] = p.actualArrivalTs
            this[t.actualDepartureTs] = p.actualDepartureTs
            this[t.confidenceSec] = p.confidenceSec
            this[t.computedAt] = p.computedAt
            this[t.sourceReportTs] = p.sourceReportTs
            this[t.serviceDate] = p.serviceDate
        }
    }

    /**
     * Drops this vehicle's still-open predictions (no actual arrival/departure recorded) that fell
     * out of its horizon: stops of its current trip it is already past, and any trip that isn't in
     * the horizon any more (finished, or the vehicle was reassigned). Left behind, they'd keep
     * showing up as upcoming arrivals on stop boards. With a [sourceReportTs], a row from a newer
     * source report is kept.
     */
    fun deleteOutOfHorizonPredictions(
        feedCode: String,
        vehicleId: String,
        currentTripRowId: Long,
        fromStopPathIndex: Int,
        horizonTripRowIds: Collection<Long>,
        sourceReportTs: Instant? = null,
    ) {
        val t = VehiclePredictionTable
        t.deleteWhere {
            ((t.feedCode eq feedCode) and (t.vehicleId eq vehicleId) and t.actualArrivalTs.isNull() and t.actualDepartureTs.isNull())
                .andIfNotNull(sourceReportTs?.let { t.sourceReportTs.isNull() or (t.sourceReportTs lessEq it) })
                .and(
                    ((t.tripRowId eq currentTripRowId) and (t.stopPathIndex less fromStopPathIndex)) or
                        (t.tripRowId notInList horizonTripRowIds),
                )
        }
    }

    /**
     * Records when the vehicle actually passed each of [passes] on its predictions for those stops
     * (every algorithm): its departure for the first stop of a trip, its arrival for any other.
     */
    fun fillActuals(
        feedCode: String,
        vehicleId: String,
        tripRowId: Long,
        passes: Map<Int, Instant>,
        reportGapSec: Int? = null,
        serviceDate: LocalDate? = null,
    ) {
        val t = VehiclePredictionTable
        val firstStop = t.stopPathIndex eq intLiteral(0)
        val statements =
            passes.map { (stopPathIndex, actualTs) ->
                val actual = param(t.actualArrivalTs, actualTs)
                buildStatement {
                    t.update({
                        (
                            (t.feedCode eq feedCode) and (t.vehicleId eq vehicleId) and (t.tripRowId eq tripRowId) and
                                (t.stopPathIndex eq stopPathIndex)
                        ).andIfNotNull(serviceDate?.let { t.serviceDate eq it })
                    }) {
                        it[t.actualArrivalTs] = case().When<Instant?>(firstStop, t.actualArrivalTs).Else(actual)
                        it[t.actualDepartureTs] = case().When<Instant?>(firstStop, actual).Else(t.actualDepartureTs)
                        it[t.actualReportGapSec] = reportGapSec
                    }
                }
            }
        currentTransaction().execBatch(statements)
    }

    /**
     * Forgets a frequency-based trip's previous run for this vehicle before it starts the next one:
     * all its previous predictions and unscored checkpoints. Historical raw crossings are retained.
     */
    fun clearPreviousRun(
        feedCode: String,
        vehicleId: String,
        tripRowId: Long,
    ) {
        val p = VehiclePredictionTable
        p.deleteWhere { (p.feedCode eq feedCode) and (p.vehicleId eq vehicleId) and (p.tripRowId eq tripRowId) }
        val c = PredictionCheckpointTable
        c.deleteWhere { (c.feedCode eq feedCode) and (c.vehicleId eq vehicleId) and (c.tripRowId eq tripRowId) }
    }

    /**
     * Keeps the first report-time cohort in a fixed sampling slot, including missing predictions.
     * Later reports cannot fill that cohort with different observation times. Stops already passed
     * get no snapshots. Old snapshots beyond the longest scored lead are pruned per vehicle.
     *
     * Each candidate is one `INSERT ... SELECT ... WHERE NOT EXISTS ... ON CONFLICT
     * (<uq_prediction_checkpoint_key columns>) DO NOTHING`, all in one batch.
     */
    fun insertCheckpoints(
        candidates: List<CheckpointCandidate>,
        staleBefore: Instant,
    ) {
        val first = candidates.firstOrNull() ?: return
        val c = PredictionCheckpointTable
        val p = VehiclePredictionTable
        c.deleteWhere {
            (c.feedCode eq first.feedCode) and (c.vehicleId eq first.vehicleId) and (coalesce(c.producedAt, c.computedAt) less staleBefore)
        }
        val columns =
            listOf(
                c.feedCode, c.vehicleId, c.tripRowId, c.stopPathIndex, c.algorithm, c.predictedTs, c.computedAt,
                c.sampleSlot, c.confidenceSec, c.serviceDate, c.producedAt, c.id,
            )
        val statements =
            candidates.map { k ->
                val passed =
                    p.select(intLiteral(1)).where {
                        (p.feedCode eq k.feedCode) and (p.vehicleId eq k.vehicleId) and (p.tripRowId eq k.tripRowId) and
                            (p.stopPathIndex eq k.stopPathIndex) and
                            IsNotDistinctFromOp(p.serviceDate, param(p.serviceDate, k.serviceDate)) and
                            (p.actualArrivalTs.isNotNull() or p.actualDepartureTs.isNotNull())
                    }
                val otherCohort =
                    c.select(intLiteral(1)).where {
                        (c.feedCode eq k.feedCode) and (c.vehicleId eq k.vehicleId) and (c.tripRowId eq k.tripRowId) and
                            (c.stopPathIndex eq k.stopPathIndex) and (c.sampleSlot eq k.sampleSlot) and
                            ((c.computedAt neq k.computedAt) or IsDistinctFromOp(c.producedAt, param(c.producedAt, k.producedAt)))
                    }
                val values =
                    Table.Dual
                        .select(
                            param(c.feedCode, k.feedCode),
                            param(c.vehicleId, k.vehicleId),
                            param(c.tripRowId, k.tripRowId),
                            param(c.stopPathIndex, k.stopPathIndex),
                            param(c.algorithm, k.algorithm),
                            param(c.predictedTs, k.predictedTs),
                            param(c.computedAt, k.computedAt),
                            param(c.sampleSlot, k.sampleSlot),
                            param(c.confidenceSec, k.confidenceSec),
                            param(c.serviceDate, k.serviceDate),
                            param(c.producedAt, k.producedAt),
                            c.idSequence.nextLongVal(),
                        ).where { notExists(passed) and notExists(otherCohort) }
                InsertSelectOnConflictDoNothing(
                    columns,
                    values,
                    conflictTarget = listOf(c.feedCode, c.vehicleId, c.tripRowId, c.stopPathIndex, c.algorithm, c.sampleSlot),
                )
            }
        currentTransaction().execBatch(statements)
    }

    /** Removes and returns this vehicle's checkpoints for the stops of a trip it just passed. */
    fun takeCheckpoints(
        feedCode: String,
        vehicleId: String,
        tripRowId: Long,
        stopPathIndexes: Collection<Int>,
    ): List<PredictionCheckpoint> {
        if (stopPathIndexes.isEmpty()) return emptyList()
        val c = PredictionCheckpointTable
        val rows =
            c
                .selectAll()
                .where {
                    (c.feedCode eq feedCode) and (c.vehicleId eq vehicleId) and (c.tripRowId eq tripRowId) and
                        (c.stopPathIndex inList stopPathIndexes)
                }.map(c::read)
        if (rows.isNotEmpty()) {
            val ids = rows.map { it.id!! }
            c.deleteWhere { c.id inList ids }
        }
        return rows
    }

    fun insertAccuracy(rows: List<PredictionAccuracy>) {
        if (rows.isEmpty()) return
        PredictionAccuracyTable.batchInsertWithoutResults(rows) { PredictionAccuracyTable.bind(this, it) }
    }

    fun insertCrossings(crossings: List<AvlStopCrossing>) {
        if (crossings.isEmpty()) return
        AvlStopCrossingTable.batchInsertWithoutResults(crossings) { AvlStopCrossingTable.bind(this, it) }
    }

    private companion object {
        fun currentTransaction(): JdbcTransaction = TransactionManager.current()

        /**
         * `pg_advisory_xact_lock(hashtext(namespace), hashtext(key))`: a transaction-scoped lock on
         * the pair, released when the transaction ends.
         */
        fun lockKey(
            namespace: String,
            key: String,
        ) {
            Table.Dual
                .select(CustomFunction("pg_advisory_xact_lock", TextColumnType(), hashtext(namespace), hashtext(key)))
                .toList()
        }

        fun hashtext(value: String) = CustomFunction("hashtext", IntegerColumnType(), stringParam(value))

        /** `greatest(<table>.<column>, EXCLUDED.<column>)`, for an upsert's update clause. */
        fun greatestOfStoredAndInserted(column: Column<Instant>) =
            CustomFunction("greatest", column.columnType, column, insertValue(column))

        /** [value] as a parameter bound the way [column] binds its values. */
        fun <T> param(
            column: Column<T>,
            value: T,
        ): QueryParameter<T> = QueryParameter(value, column.columnType)

        fun UpdateBuilder<*>.setBucketKey(
            table: LearnerBucketColumns,
            key: LearningKey,
            b: LearnerBucket,
        ) {
            this[table.gtfsFeedCode] = key.gtfsFeedCode
            this[table.patternKey] = key.patternKey
            this[table.stopPathIndex] = b.stopPathIndex
            this[table.dayType] = b.bucket.dayType
            this[table.timeBand] = b.bucket.timeBand
        }

        /** The order learner rows are locked in; see [lockObservations]. */
        val BUCKET_ORDER: Comparator<Map.Entry<LearnerBucket, *>> =
            compareBy({ it.key.stopPathIndex }, { it.key.bucket.dayType.ordinal }, { it.key.bucket.timeBand.ordinal })
    }
}
