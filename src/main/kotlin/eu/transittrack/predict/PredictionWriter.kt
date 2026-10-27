package eu.transittrack.predict

import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.Types
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import jakarta.persistence.EntityManagerFactory

import org.hibernate.SessionFactory
import org.hibernate.StatelessSession
import org.springframework.stereotype.Component

import eu.transittrack.predict.learn.DayType
import eu.transittrack.predict.learn.KalmanState
import eu.transittrack.predict.learn.LearningKey
import eu.transittrack.predict.learn.RunningAverageState
import eu.transittrack.predict.learn.TimeBand
import eu.transittrack.predict.learn.TravelTimeBucket
import eu.transittrack.predict.model.AvlStopCrossing
import eu.transittrack.predict.model.PredictionAccuracy
import eu.transittrack.predict.model.PredictionCheckpoint

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
 * are native `INSERT ... ON CONFLICT` upserts keyed on
 * `(gtfs_feed_code, pattern_key, stop_path_index, day_type, time_band)`;
 * `vehicle_prediction` is a native upsert keyed on
 * `(feed_code, vehicle_id, trip_row_id, stop_path_index, algorithm)`. Their `id` columns default to
 * their sequence, so they are left out of the inserts entirely. `prediction_accuracy` and
 * `avl_stop_crossing` are plain appends via a Hibernate [StatelessSession] insert.
 *
 * Writes that come in sets (a report's predictions, its stop passes, their learner rows) go out as
 * one JDBC batch, or one multi-row statement, per set rather than one round trip per row.
 *
 * Every read and write takes an already-open [StatelessSession] rather than opening its own
 * transaction: [PredictionService.onMatched] can generate dozens of these per matched report (one per
 * horizon stop per algorithm, plus crossings/learning), and committing each individually made that
 * commit's fsync the dominant cost of the whole prediction pipeline. Reads go through the same
 * session too, so they see the transaction's own writes and hold no second pooled connection.
 * Callers open one transaction per `onMatched()` call via [inTransaction] and reuse its session.
 */
@Component
class PredictionWriter(
    emf: EntityManagerFactory,
) {
    private val sessionFactory: SessionFactory = emf.unwrap(SessionFactory::class.java)

    fun inTransaction(block: (StatelessSession) -> Unit) {
        sessionFactory.inStatelessTransaction { session -> block(session) }
    }

    fun frequencyRun(
        session: StatelessSession,
        feedCode: String,
        vehicleId: String,
        tripRowId: Long,
    ): FrequencyRunState? =
        session.doReturningWork { conn: Connection ->
            lockKey(conn, "transittrack.predict.frequency:$feedCode", "${vehicleId.length}:$vehicleId:$tripRowId")
            conn
                .prepareStatement(
                    "select service_date, run_started_at, last_report_ts, start_time_sec, last_distance_along_trip_m " +
                        "from prediction_frequency_run " +
                        "where feed_code = ? and vehicle_id = ? and trip_row_id = ? for update",
                ).use { ps ->
                    ps.setString(1, feedCode)
                    ps.setString(2, vehicleId)
                    ps.setLong(3, tripRowId)
                    ps.executeQuery().use { rs ->
                        if (!rs.next()) return@doReturningWork null
                        val startTime = rs.getInt("start_time_sec").let { if (rs.wasNull()) null else it }
                        val distance = rs.getDouble("last_distance_along_trip_m").let { if (rs.wasNull()) null else it }
                        FrequencyRunState(
                            rs.getObject("service_date", LocalDate::class.java),
                            rs.getObject("run_started_at", OffsetDateTime::class.java).toInstant(),
                            rs.getObject("last_report_ts", OffsetDateTime::class.java).toInstant(),
                            startTime,
                            distance,
                        )
                    }
                }
        }

    fun saveFrequencyRun(
        session: StatelessSession,
        feedCode: String,
        vehicleId: String,
        tripRowId: Long,
        state: FrequencyRunState,
    ) {
        batch(session, UPSERT_FREQUENCY_RUN_SQL, listOf(state)) { ps, run ->
            ps.setString(1, feedCode)
            ps.setString(2, vehicleId)
            ps.setLong(3, tripRowId)
            ps.setObject(4, run.serviceDate)
            ps.setTimestamp(5, run.startedAt)
            ps.setTimestamp(6, run.lastReportTs)
            ps.setNullable(7, run.startTimeSec, Types.INTEGER)
            ps.setNullable(8, run.lastDistanceAlongTripM, Types.DOUBLE)
        }
    }

    /**
     * When this vehicle last passed each stop of a trip, no earlier than [since] (an earlier pass
     * belongs to the same trip row on another service day).
     */
    fun lastPasses(
        session: StatelessSession,
        feedCode: String,
        vehicleId: String,
        tripRowId: Long,
        since: Instant,
    ): Map<Int, Instant> = passObservations(session, feedCode, vehicleId, tripRowId, since).associate { it.stopPathIndex to it.observedAt }

    fun passObservations(
        session: StatelessSession,
        feedCode: String,
        vehicleId: String,
        tripRowId: Long,
        since: Instant,
        until: Instant? = null,
    ): List<AvlStopCrossing> =
        session
            .createSelectionQuery(
                "from AvlStopCrossing c " +
                    "where c.feedCode = :feedCode and c.vehicleId = :vehicleId and c.tripRowId = :tripRowId " +
                    "and c.observedAt >= :since and c.observedAt <= coalesce(:until, c.observedAt) order by c.observedAt",
                AvlStopCrossing::class.java,
            ).setParameter("feedCode", feedCode)
            .setParameter("vehicleId", vehicleId)
            .setParameter("tripRowId", tripRowId)
            .setParameter("since", since)
            .setParameter("until", until)
            .resultList
            .let { rows ->
                val runStartedAt = rows.lastOrNull()?.runStartedAt
                if (runStartedAt == null) rows else rows.filter { !it.observedAt.isBefore(runStartedAt) }
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
        session: StatelessSession,
        key: LearningKey,
        buckets: Collection<LearnerBucket>,
    ): Map<LearnerBucket, RunningAverageState> =
        lockBucketRows(session, "travel_time_observation", "sample_count, mean_sec", key, buckets) { rs ->
            RunningAverageState(rs.getLong("sample_count"), rs.getDouble("mean_sec"))
        }

    /** See [lockObservations]. */
    fun lockKalmanStates(
        session: StatelessSession,
        key: LearningKey,
        buckets: Collection<LearnerBucket>,
        observedAt: Instant? = null,
        varianceGrowthPerHour: Double = 0.0,
    ): Map<LearnerBucket, KalmanState> =
        lockBucketRows(session, "kalman_travel_time_state", "estimate_sec, error_variance, sample_count, updated_at", key, buckets) { rs ->
            val ageHours = observedAt?.let {
                Duration.between(rs.getObject("updated_at", OffsetDateTime::class.java).toInstant(), it).seconds.coerceAtLeast(0) / 3600.0
            } ?: 0.0
            KalmanState(
                rs.getDouble("estimate_sec"),
                rs.getDouble("error_variance") + ageHours * varianceGrowthPerHour,
                rs.getLong("sample_count"),
            )
        }

    private fun <T : Any> lockBucketRows(
        session: StatelessSession,
        table: String,
        columns: String,
        key: LearningKey,
        buckets: Collection<LearnerBucket>,
        read: (java.sql.ResultSet) -> T,
    ): Map<LearnerBucket, T> {
        val keys = buckets.distinct()
        if (keys.isEmpty()) return emptyMap()
        val sql =
            "select stop_path_index, day_type, time_band, $columns from $table " +
                "where gtfs_feed_code = ? and pattern_key = ? " +
                "and (stop_path_index, day_type, time_band) in (${keys.joinToString { "(?, ?, ?)" }}) " +
                "order by stop_path_index, day_type, time_band for update"
        return session.doReturningWork { conn: Connection ->
            lockKey(conn, "transittrack.predict.learn:${key.gtfsFeedCode}", key.patternKey)
            conn.prepareStatement(sql).use { ps ->
                ps.setString(1, key.gtfsFeedCode)
                ps.setString(2, key.patternKey)
                keys.forEachIndexed { i, b ->
                    ps.setInt(3 + i * 3, b.stopPathIndex)
                    ps.setShort(
                        4 + i * 3,
                        b.bucket.dayType.ordinal
                            .toShort(),
                    )
                    ps.setShort(
                        5 + i * 3,
                        b.bucket.timeBand.ordinal
                            .toShort(),
                    )
                }
                ps.executeQuery().use { rs ->
                    val rows = HashMap<LearnerBucket, T>()
                    while (rs.next()) {
                        val bucket =
                            TravelTimeBucket(DayType.entries[rs.getInt("day_type")], TimeBand.entries[rs.getInt("time_band")])
                        rows[LearnerBucket(rs.getInt("stop_path_index"), bucket)] = read(rs)
                    }
                    rows
                }
            }
        }
    }

    fun upsertObservations(
        session: StatelessSession,
        key: LearningKey,
        rows: Map<LearnerBucket, RunningAverageState>,
        at: Instant,
    ) {
        batch(session, UPSERT_OBSERVATION_SQL, rows.entries.sortedWith(BUCKET_ORDER)) { ps, (b, state) ->
            ps.setBucketKey(key, b)
            ps.setLong(6, state.sampleCount)
            ps.setDouble(7, state.meanSec)
            ps.setObject(8, at.atOffset())
        }
    }

    fun upsertKalmanStates(
        session: StatelessSession,
        key: LearningKey,
        rows: Map<LearnerBucket, KalmanState>,
        at: Instant,
    ) {
        batch(session, UPSERT_KALMAN_STATE_SQL, rows.entries.sortedWith(BUCKET_ORDER)) { ps, (b, state) ->
            ps.setBucketKey(key, b)
            ps.setDouble(6, state.estimateSec)
            ps.setDouble(7, state.errorVariance)
            ps.setLong(8, state.sampleCount)
            ps.setObject(9, at.atOffset())
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
        session: StatelessSession,
        rows: List<VehiclePredictionUpsert>,
        staleBefore: Instant,
        refreshBefore: Instant,
        minChangeSec: Int,
    ) {
        batch(session, UPSERT_PREDICTION_SQL, rows) { ps, p ->
            ps.setString(1, p.feedCode)
            ps.setString(2, p.vehicleId)
            ps.setLong(3, p.tripRowId)
            ps.setNullable(4, p.blockPk, Types.BIGINT)
            ps.setLong(5, p.tripPatternId)
            ps.setInt(6, p.stopPathIndex)
            ps.setShort(7, p.algorithm.ordinal.toShort())
            ps.setTimestamp(8, p.predictedArrivalTs)
            ps.setTimestamp(9, p.predictedDepartureTs)
            ps.setTimestamp(10, p.actualArrivalTs)
            ps.setTimestamp(11, p.actualDepartureTs)
            ps.setNullable(12, p.confidenceSec, Types.INTEGER)
            ps.setTimestamp(13, p.computedAt)
            ps.setTimestamp(14, p.sourceReportTs)
            ps.setObject(15, p.serviceDate, Types.DATE)
            ps.setTimestamp(16, staleBefore)
            ps.setTimestamp(17, refreshBefore)
            ps.setInt(18, minChangeSec)
            ps.setInt(19, minChangeSec)
        }
    }

    /**
     * Drops this vehicle's still-open predictions (no actual arrival/departure recorded) that fell
     * out of its horizon: stops of its current trip it is already past, and any trip that isn't in
     * the horizon any more (finished, or the vehicle was reassigned). Left behind, they'd keep
     * showing up as upcoming arrivals on stop boards.
     */
    fun deleteOutOfHorizonPredictions(
        session: StatelessSession,
        feedCode: String,
        vehicleId: String,
        currentTripRowId: Long,
        fromStopPathIndex: Int,
        horizonTripRowIds: Collection<Long>,
        sourceReportTs: Instant? = null,
    ) {
        session
            .createNativeMutationQuery(DELETE_OUT_OF_HORIZON_SQL)
            .setParameter("feed_code", feedCode)
            .setParameter("vehicle_id", vehicleId)
            .setParameter("trip_row_id", currentTripRowId)
            .setParameter("from_index", fromStopPathIndex)
            .setParameterList("horizon_trip_row_ids", horizonTripRowIds)
            .setParameter("source_report_ts", sourceReportTs)
            .executeUpdate()
    }

    /**
     * Records when the vehicle actually passed each of [passes] on its predictions for those stops
     * (every algorithm): its departure for the first stop of a trip, its arrival for any other.
     */
    fun fillActuals(
        session: StatelessSession,
        feedCode: String,
        vehicleId: String,
        tripRowId: Long,
        passes: Map<Int, Instant>,
        reportGapSec: Int? = null,
        serviceDate: LocalDate? = null,
    ) {
        batch(session, FILL_ACTUAL_SQL, passes.entries.toList()) { ps, (stopPathIndex, actualTs) ->
            ps.setTimestamp(1, actualTs)
            ps.setTimestamp(2, actualTs)
            ps.setNullable(3, reportGapSec, Types.INTEGER)
            ps.setString(4, feedCode)
            ps.setString(5, vehicleId)
            ps.setLong(6, tripRowId)
            ps.setInt(7, stopPathIndex)
            ps.setObject(8, serviceDate, Types.DATE)
            ps.setObject(9, serviceDate, Types.DATE)
        }
    }

    /**
     * Forgets a frequency-based trip's previous run for this vehicle before it starts the next one:
     * all its previous predictions and unscored checkpoints. Historical raw crossings are retained.
     */
    fun clearPreviousRun(
        session: StatelessSession,
        feedCode: String,
        vehicleId: String,
        tripRowId: Long,
    ) {
        session
            .createMutationQuery(
                "delete from VehiclePrediction p where p.feedCode = :feedCode and p.vehicleId = :vehicleId " +
                    "and p.tripRowId = :tripRowId",
            ).setParameter("feedCode", feedCode)
            .setParameter("vehicleId", vehicleId)
            .setParameter("tripRowId", tripRowId)
            .executeUpdate()
        session
            .createMutationQuery(
                "delete from PredictionCheckpoint c where c.feedCode = :feedCode and c.vehicleId = :vehicleId " +
                    "and c.tripRowId = :tripRowId",
            ).setParameter("feedCode", feedCode)
            .setParameter("vehicleId", vehicleId)
            .setParameter("tripRowId", tripRowId)
            .executeUpdate()
    }

    /**
     * Keeps the first report-time cohort in a fixed sampling slot, including missing predictions.
     * Later reports cannot fill that cohort with different observation times. Stops already passed
     * get no snapshots. Old snapshots beyond the longest scored lead are pruned per vehicle.
     */
    fun insertCheckpoints(
        session: StatelessSession,
        candidates: List<CheckpointCandidate>,
        staleBefore: Instant,
    ) {
        val first = candidates.firstOrNull() ?: return
        session
            .createMutationQuery(
                "delete from PredictionCheckpoint c where c.feedCode = :feedCode and c.vehicleId = :vehicleId " +
                    "and coalesce(c.producedAt, c.computedAt) < :since",
            ).setParameter("feedCode", first.feedCode)
            .setParameter("vehicleId", first.vehicleId)
            .setParameter("since", staleBefore)
            .executeUpdate()
        batch(session, INSERT_CHECKPOINT_SQL, candidates) { ps, c ->
            ps.setString(1, c.feedCode)
            ps.setString(2, c.vehicleId)
            ps.setLong(3, c.tripRowId)
            ps.setInt(4, c.stopPathIndex)
            ps.setShort(5, c.algorithm.ordinal.toShort())
            ps.setTimestamp(6, c.predictedTs)
            ps.setTimestamp(7, c.computedAt)
            ps.setTimestamp(8, c.sampleSlot)
            ps.setNullable(9, c.confidenceSec, Types.INTEGER)
            ps.setObject(10, c.serviceDate, Types.DATE)
            ps.setTimestamp(11, c.producedAt)
            ps.setString(12, c.feedCode)
            ps.setString(13, c.vehicleId)
            ps.setLong(14, c.tripRowId)
            ps.setInt(15, c.stopPathIndex)
            ps.setObject(16, c.serviceDate, Types.DATE)
            ps.setString(17, c.feedCode)
            ps.setString(18, c.vehicleId)
            ps.setLong(19, c.tripRowId)
            ps.setInt(20, c.stopPathIndex)
            ps.setTimestamp(21, c.sampleSlot)
            ps.setTimestamp(22, c.computedAt)
            ps.setTimestamp(23, c.producedAt)
        }
    }

    /** Removes and returns this vehicle's checkpoints for the stops of a trip it just passed. */
    fun takeCheckpoints(
        session: StatelessSession,
        feedCode: String,
        vehicleId: String,
        tripRowId: Long,
        stopPathIndexes: Collection<Int>,
    ): List<PredictionCheckpoint> {
        if (stopPathIndexes.isEmpty()) return emptyList()
        val rows =
            session
                .createSelectionQuery(
                    "from PredictionCheckpoint c where c.feedCode = :feedCode and c.vehicleId = :vehicleId " +
                        "and c.tripRowId = :tripRowId and c.stopPathIndex in :stopPathIndexes",
                    PredictionCheckpoint::class.java,
                ).setParameter("feedCode", feedCode)
                .setParameter("vehicleId", vehicleId)
                .setParameter("tripRowId", tripRowId)
                .setParameterList("stopPathIndexes", stopPathIndexes)
                .resultList
        if (rows.isNotEmpty()) {
            session
                .createMutationQuery("delete from PredictionCheckpoint c where c.id in :ids")
                .setParameterList("ids", rows.map { it.id!! })
                .executeUpdate()
        }
        return rows
    }

    fun insertAccuracy(
        session: StatelessSession,
        rows: List<PredictionAccuracy>,
    ) {
        if (rows.isNotEmpty()) session.insertMultiple(rows)
    }

    fun insertCrossings(
        session: StatelessSession,
        crossings: List<AvlStopCrossing>,
    ) {
        if (crossings.isNotEmpty()) session.insertMultiple(crossings)
    }

    /** Runs [sql] once per row of [rows] as a single JDBC batch. */
    private fun <T> batch(
        session: StatelessSession,
        sql: String,
        rows: List<T>,
        bind: (PreparedStatement, T) -> Unit,
    ) {
        if (rows.isEmpty()) return
        session.doWork { conn: Connection ->
            conn.prepareStatement(sql).use { ps ->
                for (row in rows) {
                    bind(ps, row)
                    ps.addBatch()
                }
                ps.executeBatch()
            }
        }
    }

    private companion object {
        fun lockKey(
            connection: Connection,
            namespace: String,
            key: String,
        ) {
            connection.prepareStatement("select pg_advisory_xact_lock(hashtext(?), hashtext(?))").use { ps ->
                ps.setString(1, namespace)
                ps.setString(2, key)
                ps.executeQuery().use { it.next() }
            }
        }

        fun Instant.atOffset(): OffsetDateTime = OffsetDateTime.ofInstant(this, ZoneOffset.UTC)

        fun PreparedStatement.setTimestamp(
            index: Int,
            ts: Instant?,
        ) = if (ts == null) setNull(index, Types.TIMESTAMP_WITH_TIMEZONE) else setObject(index, ts.atOffset())

        fun PreparedStatement.setNullable(
            index: Int,
            value: Number?,
            sqlType: Int,
        ) = if (value == null) setNull(index, sqlType) else setObject(index, value, sqlType)

        fun PreparedStatement.setBucketKey(
            key: LearningKey,
            b: LearnerBucket,
        ) {
            setString(1, key.gtfsFeedCode)
            setString(2, key.patternKey)
            setInt(3, b.stopPathIndex)
            setShort(
                4,
                b.bucket.dayType.ordinal
                    .toShort(),
            )
            setShort(
                5,
                b.bucket.timeBand.ordinal
                    .toShort(),
            )
        }

        /** The order learner rows are locked in; see [lockObservations]. */
        val BUCKET_ORDER: Comparator<Map.Entry<LearnerBucket, *>> =
            compareBy({ it.key.stopPathIndex }, { it.key.bucket.dayType.ordinal }, { it.key.bucket.timeBand.ordinal })

        const val UPSERT_FREQUENCY_RUN_SQL = """
            insert into prediction_frequency_run
                (feed_code, vehicle_id, trip_row_id, service_date, run_started_at, last_report_ts,
                 start_time_sec, last_distance_along_trip_m)
            values (?, ?, ?, ?, ?, ?, ?, ?)
            on conflict (feed_code, vehicle_id, trip_row_id) do update set
                service_date = excluded.service_date, run_started_at = excluded.run_started_at,
                last_report_ts = excluded.last_report_ts, start_time_sec = excluded.start_time_sec,
                last_distance_along_trip_m = excluded.last_distance_along_trip_m
            where prediction_frequency_run.last_report_ts <= excluded.last_report_ts
        """

        const val FILL_ACTUAL_SQL = """
            update vehicle_prediction set
                actual_arrival_ts = case when stop_path_index = 0 then actual_arrival_ts else ? end,
                actual_departure_ts = case when stop_path_index = 0 then ? else actual_departure_ts end,
                actual_report_gap_sec = ?
            where feed_code = ? and vehicle_id = ? and trip_row_id = ? and stop_path_index = ?
              and (? is null or service_date = ?)
        """

        const val INSERT_CHECKPOINT_SQL = """
            insert into prediction_checkpoint (
                feed_code, vehicle_id, trip_row_id, stop_path_index, algorithm, predicted_ts, computed_at,
                sample_slot, confidence_sec, service_date, produced_at
            )
            select ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
            where not exists (
                select 1 from vehicle_prediction
                where feed_code = ? and vehicle_id = ? and trip_row_id = ? and stop_path_index = ?
                  and service_date is not distinct from ?
                  and (actual_arrival_ts is not null or actual_departure_ts is not null)
            )
              and not exists (
                select 1 from prediction_checkpoint
                where feed_code = ? and vehicle_id = ? and trip_row_id = ? and stop_path_index = ?
                  and sample_slot = ? and (computed_at <> ? or produced_at is distinct from ?)
              )
            on conflict (feed_code, vehicle_id, trip_row_id, stop_path_index, algorithm, sample_slot) do nothing
        """

        const val UPSERT_OBSERVATION_SQL = """
            insert into travel_time_observation (
                gtfs_feed_code, pattern_key, stop_path_index, day_type, time_band, sample_count, mean_sec, updated_at
            ) values (?, ?, ?, ?, ?, ?, ?, ?)
            on conflict (gtfs_feed_code, pattern_key, stop_path_index, day_type, time_band) do update set
                sample_count = excluded.sample_count,
                mean_sec = excluded.mean_sec,
                updated_at = greatest(travel_time_observation.updated_at, excluded.updated_at)
        """

        const val UPSERT_KALMAN_STATE_SQL = """
            insert into kalman_travel_time_state (
                gtfs_feed_code, pattern_key, stop_path_index, day_type, time_band,
                estimate_sec, error_variance, sample_count, updated_at
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?)
            on conflict (gtfs_feed_code, pattern_key, stop_path_index, day_type, time_band) do update set
                estimate_sec = excluded.estimate_sec,
                error_variance = excluded.error_variance,
                sample_count = excluded.sample_count,
                updated_at = greatest(kalman_travel_time_state.updated_at, excluded.updated_at)
        """

        const val UPSERT_PREDICTION_SQL = """
            insert into vehicle_prediction (
                feed_code, vehicle_id, trip_row_id, block_pk, trip_pattern_id, stop_path_index, algorithm,
                predicted_arrival_ts, predicted_departure_ts, actual_arrival_ts, actual_departure_ts,
                confidence_sec, computed_at, source_report_ts, service_date
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            on conflict (feed_code, vehicle_id, trip_row_id, stop_path_index, algorithm) do update set
                block_pk = excluded.block_pk,
                trip_pattern_id = excluded.trip_pattern_id,
                predicted_arrival_ts = excluded.predicted_arrival_ts,
                predicted_departure_ts = excluded.predicted_departure_ts,
                actual_arrival_ts = excluded.actual_arrival_ts,
                actual_departure_ts = excluded.actual_departure_ts,
                actual_report_gap_sec = null,
                confidence_sec = excluded.confidence_sec,
                computed_at = excluded.computed_at,
                source_report_ts = excluded.source_report_ts,
                service_date = excluded.service_date
            where (vehicle_prediction.source_report_ts is null or vehicle_prediction.source_report_ts <= excluded.source_report_ts)
              and (vehicle_prediction.service_date is distinct from excluded.service_date
               or vehicle_prediction.computed_at < ?
               or (vehicle_prediction.actual_arrival_ts is null and vehicle_prediction.actual_departure_ts is null
                   and (vehicle_prediction.computed_at < ?
                        or vehicle_prediction.block_pk is distinct from excluded.block_pk
                        or vehicle_prediction.trip_pattern_id <> excluded.trip_pattern_id
                        or (vehicle_prediction.predicted_arrival_ts is null) <> (excluded.predicted_arrival_ts is null)
                        or (vehicle_prediction.predicted_departure_ts is null) <> (excluded.predicted_departure_ts is null)
                        or abs(extract(epoch from excluded.predicted_arrival_ts - vehicle_prediction.predicted_arrival_ts)) >= ?
                        or abs(extract(epoch from excluded.predicted_departure_ts - vehicle_prediction.predicted_departure_ts)) >= ?)))
        """

        const val DELETE_OUT_OF_HORIZON_SQL = """
            delete from vehicle_prediction
            where feed_code = :feed_code and vehicle_id = :vehicle_id
              and actual_arrival_ts is null and actual_departure_ts is null
              and (cast(:source_report_ts as timestamptz) is null or source_report_ts is null
                   or source_report_ts <= cast(:source_report_ts as timestamptz))
              and ((trip_row_id = :trip_row_id and stop_path_index < :from_index)
                   or trip_row_id not in (:horizon_trip_row_ids))
        """
    }
}
