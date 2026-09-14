package eu.transittrack.predict.model

import java.time.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate

import eu.transittrack.gtfs.support.PostgresSliceTest
import eu.transittrack.predict.PredictionAlgorithm
import eu.transittrack.support.PostgresPerMethodTest

@PostgresSliceTest
class PredictionEntitiesPersistenceTest(
    @Autowired val observations: TravelTimeObservationRepository,
    @Autowired val kalmanStates: KalmanTravelTimeStateRepository,
    @Autowired val predictions: VehiclePredictionRepository,
    @Autowired val accuracies: PredictionAccuracyRepository,
    @Autowired val jdbcTemplate: JdbcTemplate,
) : PostgresPerMethodTest() {
    // A plain JDBC insert (not an EntityManager native query) - the latter needs an active
    // transaction, which NOT_SUPPORTED deliberately does not provide.
    @BeforeTest
    fun seedFeed() {
        jdbcTemplate.update(
            "insert into avl_feed " +
                "(id, code, name, gtfs_feed_code, url, format, poll_interval_sec, assignment_mode, " +
                "prediction_algorithm, prediction_mode, enabled, source, created_at, updated_at) values " +
                "(1, 'feed-1', 'feed', 'g', 'http://x', 0, 30, 0, 0, 0, true, 'CONFIG', now(), now())",
        )
    }

    @Test
    fun `travel_time_observation round-trips`() {
        observations.save(
            TravelTimeObservation(
                tripPatternId = 10L, stopPathIndex = 2, sampleCount = 5L, meanSec = 42.5,
                updatedAt = Instant.parse("2026-09-04T10:00:00Z"),
            ),
        )
        val found = observations.findByTripPatternIdAndStopPathIndex(10L, 2)
        assertThat(found).isNotNull()
        assertThat(found!!.meanSec).isEqualTo(42.5)
    }

    @Test
    fun `kalman_travel_time_state round-trips`() {
        kalmanStates.save(
            KalmanTravelTimeState(
                tripPatternId = 11L, stopPathIndex = 3, estimateSec = 30.0, errorVariance = 400.0,
                sampleCount = 1L, updatedAt = Instant.parse("2026-09-04T10:00:00Z"),
            ),
        )
        val found = kalmanStates.findByTripPatternIdAndStopPathIndex(11L, 3)
        assertThat(found).isNotNull()
        assertThat(found!!.estimateSec).isEqualTo(30.0)
    }

    @Test
    fun `vehicle_prediction ordered by stop_path_index`() {
        predictions.save(
            VehiclePrediction(
                feedId = 1L, vehicleId = "v1", tripRowId = 7L, blockPk = null, tripPatternId = 4L,
                stopPathIndex = 3, algorithm = PredictionAlgorithm.KALMAN,
                predictedArrivalTs = Instant.parse("2026-09-04T10:05:00Z"), predictedDepartureTs = null,
                actualArrivalTs = null, actualDepartureTs = null, confidenceSec = 30,
                computedAt = Instant.parse("2026-09-04T10:00:00Z"),
            ),
        )
        predictions.save(
            VehiclePrediction(
                feedId = 1L, vehicleId = "v1", tripRowId = 7L, blockPk = null, tripPatternId = 4L,
                stopPathIndex = 1, algorithm = PredictionAlgorithm.KALMAN,
                predictedArrivalTs = Instant.parse("2026-09-04T10:01:00Z"), predictedDepartureTs = null,
                actualArrivalTs = null, actualDepartureTs = null, confidenceSec = 30,
                computedAt = Instant.parse("2026-09-04T10:00:00Z"),
            ),
        )
        val ordered = predictions.findByFeedIdAndVehicleIdAndTripRowIdOrderByStopPathIndex(1L, "v1", 7L)
        assertThat(ordered).hasSize(2)
        assertThat(ordered.first().stopPathIndex).isEqualTo(1)
        assertThat(ordered.last().stopPathIndex).isEqualTo(3)

        val byPattern = predictions.findByTripPatternIdAndStopPathIndexAndAlgorithm(4L, 1, PredictionAlgorithm.KALMAN)
        assertThat(byPattern).hasSize(1)
    }

    @Test
    fun `prediction_accuracy filters by created_at`() {
        accuracies.save(
            PredictionAccuracy(
                feedId = 1L, vehicleId = "v2", tripRowId = 8L, stopPathIndex = 1,
                algorithm = PredictionAlgorithm.HISTORICAL_AVERAGE,
                predictedTs = Instant.parse("2026-09-04T10:00:00Z"),
                actualTs = Instant.parse("2026-09-04T10:00:10Z"),
                errorSec = 10, absErrorSec = 10,
                createdAt = Instant.parse("2026-09-04T09:00:00Z"),
            ),
        )
        accuracies.save(
            PredictionAccuracy(
                feedId = 1L, vehicleId = "v2", tripRowId = 8L, stopPathIndex = 1,
                algorithm = PredictionAlgorithm.HISTORICAL_AVERAGE,
                predictedTs = Instant.parse("2026-09-04T11:00:00Z"),
                actualTs = Instant.parse("2026-09-04T11:00:05Z"),
                errorSec = 5, absErrorSec = 5,
                createdAt = Instant.parse("2026-09-04T11:00:00Z"),
            ),
        )
        val recent = accuracies.findByFeedIdAndAlgorithmAndCreatedAtAfter(
            1L, PredictionAlgorithm.HISTORICAL_AVERAGE, Instant.parse("2026-09-04T10:00:00Z"),
        )
        assertThat(recent).hasSize(1)
        assertThat(recent.first().errorSec).isEqualTo(5)
    }
}
