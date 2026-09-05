package eu.transittrack.predict

import java.time.Instant
import jakarta.persistence.EntityManagerFactory

import org.hibernate.SessionFactory
import org.springframework.stereotype.Component

import eu.transittrack.predict.model.PredictionAccuracy

data class VehiclePredictionUpsert(
    val feedId: Long,
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
)

/**
 * Persistence for the prediction pipeline. `travel_time_observation` and `kalman_travel_time_state`
 * are native `INSERT ... ON CONFLICT` upserts keyed on `(trip_pattern_id, stop_path_index)`;
 * `vehicle_prediction` is a native upsert keyed on
 * `(feed_id, vehicle_id, trip_row_id, stop_path_index, algorithm)`. All three `id` columns are
 * Postgres IDENTITY, so they are left out of the inserts entirely. `prediction_accuracy` is a plain
 * append via a Hibernate [org.hibernate.StatelessSession] insert (JDBC-batched, no persistence
 * context).
 */
@Component
class PredictionWriter(
    emf: EntityManagerFactory,
) {
    private val sessionFactory: SessionFactory = emf.unwrap(SessionFactory::class.java)

    fun upsertObservation(
        tripPatternId: Long,
        stopPathIndex: Int,
        sampleCount: Long,
        meanSec: Double,
        at: Instant,
    ) {
        sessionFactory.inStatelessTransaction { session ->
            session
                .createNativeMutationQuery(UPSERT_OBSERVATION_SQL)
                .setParameter("trip_pattern_id", tripPatternId)
                .setParameter("stop_path_index", stopPathIndex)
                .setParameter("sample_count", sampleCount)
                .setParameter("mean_sec", meanSec)
                .setParameter("updated_at", at)
                .executeUpdate()
        }
    }

    fun upsertKalmanState(
        tripPatternId: Long,
        stopPathIndex: Int,
        estimateSec: Double,
        errorVariance: Double,
        sampleCount: Long,
        at: Instant,
    ) {
        sessionFactory.inStatelessTransaction { session ->
            session
                .createNativeMutationQuery(UPSERT_KALMAN_STATE_SQL)
                .setParameter("trip_pattern_id", tripPatternId)
                .setParameter("stop_path_index", stopPathIndex)
                .setParameter("estimate_sec", estimateSec)
                .setParameter("error_variance", errorVariance)
                .setParameter("sample_count", sampleCount)
                .setParameter("updated_at", at)
                .executeUpdate()
        }
    }

    fun upsertPrediction(p: VehiclePredictionUpsert) {
        sessionFactory.inStatelessTransaction { session ->
            session
                .createNativeMutationQuery(UPSERT_PREDICTION_SQL)
                .setParameter("feed_id", p.feedId)
                .setParameter("vehicle_id", p.vehicleId)
                .setParameter("trip_row_id", p.tripRowId)
                .setParameter("block_pk", p.blockPk)
                .setParameter("trip_pattern_id", p.tripPatternId)
                .setParameter("stop_path_index", p.stopPathIndex)
                .setParameter("algorithm", p.algorithm.ordinal)
                .setParameter("predicted_arrival_ts", p.predictedArrivalTs)
                .setParameter("predicted_departure_ts", p.predictedDepartureTs)
                .setParameter("actual_arrival_ts", p.actualArrivalTs)
                .setParameter("actual_departure_ts", p.actualDepartureTs)
                .setParameter("confidence_sec", p.confidenceSec)
                .setParameter("computed_at", p.computedAt)
                .executeUpdate()
        }
    }

    fun insertAccuracy(a: PredictionAccuracy) {
        sessionFactory.inStatelessTransaction { session -> session.insert(a) }
    }

    private companion object {
        const val UPSERT_OBSERVATION_SQL = """
            insert into travel_time_observation (
                trip_pattern_id, stop_path_index, sample_count, mean_sec, updated_at
            ) values (
                :trip_pattern_id, :stop_path_index, :sample_count, :mean_sec, :updated_at
            )
            on conflict (trip_pattern_id, stop_path_index) do update set
                sample_count = excluded.sample_count,
                mean_sec = excluded.mean_sec,
                updated_at = excluded.updated_at
        """

        const val UPSERT_KALMAN_STATE_SQL = """
            insert into kalman_travel_time_state (
                trip_pattern_id, stop_path_index, estimate_sec, error_variance, sample_count, updated_at
            ) values (
                :trip_pattern_id, :stop_path_index, :estimate_sec, :error_variance, :sample_count, :updated_at
            )
            on conflict (trip_pattern_id, stop_path_index) do update set
                estimate_sec = excluded.estimate_sec,
                error_variance = excluded.error_variance,
                sample_count = excluded.sample_count,
                updated_at = excluded.updated_at
        """

        const val UPSERT_PREDICTION_SQL = """
            insert into vehicle_prediction (
                feed_id, vehicle_id, trip_row_id, block_pk, trip_pattern_id, stop_path_index, algorithm,
                predicted_arrival_ts, predicted_departure_ts, actual_arrival_ts, actual_departure_ts,
                confidence_sec, computed_at
            ) values (
                :feed_id, :vehicle_id, :trip_row_id, :block_pk, :trip_pattern_id, :stop_path_index, :algorithm,
                :predicted_arrival_ts, :predicted_departure_ts, :actual_arrival_ts, :actual_departure_ts,
                :confidence_sec, :computed_at
            )
            on conflict (feed_id, vehicle_id, trip_row_id, stop_path_index, algorithm) do update set
                block_pk = excluded.block_pk,
                trip_pattern_id = excluded.trip_pattern_id,
                predicted_arrival_ts = excluded.predicted_arrival_ts,
                predicted_departure_ts = excluded.predicted_departure_ts,
                actual_arrival_ts = coalesce(excluded.actual_arrival_ts, vehicle_prediction.actual_arrival_ts),
                actual_departure_ts = coalesce(excluded.actual_departure_ts, vehicle_prediction.actual_departure_ts),
                confidence_sec = excluded.confidence_sec,
                computed_at = excluded.computed_at
        """
    }
}
