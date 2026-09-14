package eu.transittrack.predict

import java.time.Instant
import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

import eu.transittrack.AvlAssignmentMode
import eu.transittrack.AvlFormat
import eu.transittrack.avl.model.AvlFeed
import eu.transittrack.avl.model.AvlFeedRepository
import eu.transittrack.avl.model.AvlFeedSourceKind
import eu.transittrack.gtfs.support.PostgresSliceTest
import eu.transittrack.predict.model.KalmanTravelTimeStateRepository
import eu.transittrack.predict.model.PredictionAccuracy
import eu.transittrack.predict.model.PredictionAccuracyRepository
import eu.transittrack.predict.model.TravelTimeObservationRepository
import eu.transittrack.predict.model.VehiclePredictionRepository
import eu.transittrack.support.PostgresPerMethodTest

/**
 * [PredictionWriter] commits through its own [org.hibernate.StatelessSession] transaction, so this
 * test must NOT run inside the default rollback transaction — otherwise the seeded `avl_feed` row
 * (parent of `vehicle_prediction.feed_id`/`prediction_accuracy.feed_id`) would be invisible to the
 * stateless session's connection. Hence `NOT_SUPPORTED` + explicit cleanup in `@AfterEach`.
 */
@PostgresSliceTest
@Import(PredictionWriter::class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PredictionWriterTest(
    @Autowired val writer: PredictionWriter,
    @Autowired val observations: TravelTimeObservationRepository,
    @Autowired val kalmanStates: KalmanTravelTimeStateRepository,
    @Autowired val predictions: VehiclePredictionRepository,
    @Autowired val accuracies: PredictionAccuracyRepository,
    @Autowired val feeds: AvlFeedRepository,
) : PostgresPerMethodTest() {
    private var feedId = 0L

    @BeforeEach
    fun seed() {
        feedId =
            feeds
                .save(
                    AvlFeed(
                        code = "f1", name = "F1", gtfsFeedCode = "g", url = "http://x",
                        format = AvlFormat.GTFS_RT, pollIntervalSec = 30,
                        assignmentMode = AvlAssignmentMode.TRUST_DESCRIPTOR, enabled = true,
                        headers = null, source = AvlFeedSourceKind.CONFIG,
                        createdAt = Instant.now(), updatedAt = Instant.now(),
                    ),
                ).id!!
    }

    @AfterEach
    fun cleanup() {
        predictions.deleteAll()
        accuracies.deleteAll()
        observations.deleteAll()
        kalmanStates.deleteAll()
        feeds.deleteById(feedId)
    }

    private fun prediction(predictedArrivalTs: Instant) =
        VehiclePredictionUpsert(
            feedId = feedId, vehicleId = "v1", tripRowId = 1L, blockPk = null, tripPatternId = 10L,
            stopPathIndex = 2, algorithm = PredictionAlgorithm.KALMAN,
            predictedArrivalTs = predictedArrivalTs, predictedDepartureTs = null,
            actualArrivalTs = null, actualDepartureTs = null, confidenceSec = null,
            computedAt = Instant.parse("2026-09-04T10:00:00Z"),
        )

    @Test
    fun `upsertObservation replaces the single row, latest wins`() {
        writer.upsertObservation(
            tripPatternId = 10L, stopPathIndex = 2, sampleCount = 1L, meanSec = 30.0,
            at = Instant.parse("2026-09-04T10:00:00Z"),
        )
        writer.upsertObservation(
            tripPatternId = 10L, stopPathIndex = 2, sampleCount = 2L, meanSec = 45.0,
            at = Instant.parse("2026-09-04T10:05:00Z"),
        )
        val all = observations.findAll()
        assertThat(all.size).isEqualTo(1)
        assertThat(observations.findByTripPatternIdAndStopPathIndex(10L, 2)!!.meanSec).isEqualTo(45.0)
    }

    @Test
    fun `upsertKalmanState replaces the single row, latest wins`() {
        writer.upsertKalmanState(
            tripPatternId = 10L, stopPathIndex = 2, estimateSec = 30.0, errorVariance = 5.0,
            sampleCount = 1L, at = Instant.parse("2026-09-04T10:00:00Z"),
        )
        writer.upsertKalmanState(
            tripPatternId = 10L, stopPathIndex = 2, estimateSec = 40.0, errorVariance = 3.0,
            sampleCount = 2L, at = Instant.parse("2026-09-04T10:05:00Z"),
        )
        val all = kalmanStates.findAll()
        assertThat(all.size).isEqualTo(1)
        assertThat(kalmanStates.findByTripPatternIdAndStopPathIndex(10L, 2)!!.estimateSec).isEqualTo(40.0)
    }

    @Test
    fun `upsertPrediction replaces the single row, latest wins`() {
        writer.upsertPrediction(prediction(Instant.parse("2026-09-04T10:10:00Z")))
        writer.upsertPrediction(prediction(Instant.parse("2026-09-04T10:20:00Z")))
        val all =
            predictions.findByTripPatternIdAndStopPathIndexAndAlgorithm(10L, 2, PredictionAlgorithm.KALMAN)
        assertThat(all.size).isEqualTo(1)
        assertThat(all.first().predictedArrivalTs).isEqualTo(Instant.parse("2026-09-04T10:20:00Z"))
    }

    @Test
    fun `insertAccuracy appends a row`() {
        writer.insertAccuracy(
            PredictionAccuracy(
                feedId = feedId, vehicleId = "v1", tripRowId = 1L, stopPathIndex = 2,
                algorithm = PredictionAlgorithm.KALMAN,
                predictedTs = Instant.parse("2026-09-04T10:10:00Z"),
                actualTs = Instant.parse("2026-09-04T10:12:00Z"),
                errorSec = 120, absErrorSec = 120,
                createdAt = Instant.parse("2026-09-04T10:12:00Z"),
            ),
        )
        assertThat(accuracies.count()).isEqualTo(1L)
    }
}
