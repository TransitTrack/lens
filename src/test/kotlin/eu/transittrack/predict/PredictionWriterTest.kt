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
import eu.transittrack.predict.learn.DayType
import eu.transittrack.predict.learn.LearningKey
import eu.transittrack.predict.learn.TimeBand
import eu.transittrack.predict.learn.TravelTimeBucket
import eu.transittrack.predict.model.KalmanTravelTimeStateRepository
import eu.transittrack.predict.model.PredictionAccuracy
import eu.transittrack.predict.model.PredictionAccuracyRepository
import eu.transittrack.predict.model.TravelTimeObservationRepository
import eu.transittrack.predict.model.VehiclePredictionRepository
import eu.transittrack.support.PostgresPerMethodTest

/**
 * [PredictionWriter] commits through its own [org.hibernate.StatelessSession] transaction, so this
 * test must NOT run inside the default rollback transaction — otherwise the seeded `avl_feed` row
 * (parent of `vehicle_prediction.feed_code`/`prediction_accuracy.feed_code`) would be invisible to the
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
            feedCode = "f1", vehicleId = "v1", tripRowId = 1L, blockPk = null, tripPatternId = 10L,
            stopPathIndex = 2, algorithm = PredictionAlgorithm.KALMAN,
            predictedArrivalTs = predictedArrivalTs, predictedDepartureTs = null,
            actualArrivalTs = null, actualDepartureTs = null, confidenceSec = null,
            computedAt = Instant.parse("2026-09-04T10:00:00Z"),
        )

    private val key = LearningKey("g", "RA|SHP|S1_to_S4|abc")
    private val peak = TravelTimeBucket(DayType.WEEKDAY, TimeBand.MORNING_PEAK)

    @Test
    fun `upsertObservation replaces the single row per bucket, latest wins`() {
        writer.inTransaction { session ->
            writer.upsertObservation(session, key, 2, peak, sampleCount = 1L, meanSec = 30.0, at = Instant.parse("2026-09-04T10:00:00Z"))
        }
        writer.inTransaction { session ->
            writer.upsertObservation(session, key, 2, peak, sampleCount = 2L, meanSec = 45.0, at = Instant.parse("2026-09-04T10:05:00Z"))
        }
        assertThat(observations.findAll().size).isEqualTo(1)
        assertThat(observations.findInBucket("g", key.patternKey, 2, DayType.WEEKDAY, TimeBand.MORNING_PEAK)!!.meanSec).isEqualTo(45.0)
    }

    @Test
    fun `upsertObservation keeps buckets of the same stop path apart`() {
        writer.inTransaction { session ->
            writer.upsertObservation(session, key, 2, peak, sampleCount = 1L, meanSec = 90.0, at = Instant.parse("2026-09-04T08:00:00Z"))
            writer.upsertObservation(
                session, key, 2, TravelTimeBucket.OVERALL, sampleCount = 1L, meanSec = 60.0, at = Instant.parse("2026-09-04T08:00:00Z"),
            )
        }
        assertThat(observations.findByPattern("g", key.patternKey).associate { it.timeBand to it.meanSec })
            .isEqualTo(mapOf(TimeBand.MORNING_PEAK to 90.0, TimeBand.ALL to 60.0))
    }

    @Test
    fun `upsertKalmanState replaces the single row per bucket, latest wins`() {
        writer.inTransaction { session ->
            writer.upsertKalmanState(
                session, key, 2, peak,
                estimateSec = 30.0, errorVariance = 5.0, sampleCount = 1L, at = Instant.parse("2026-09-04T10:00:00Z"),
            )
        }
        writer.inTransaction { session ->
            writer.upsertKalmanState(
                session, key, 2, peak,
                estimateSec = 40.0, errorVariance = 3.0, sampleCount = 2L, at = Instant.parse("2026-09-04T10:05:00Z"),
            )
        }
        assertThat(kalmanStates.findAll().size).isEqualTo(1)
        assertThat(kalmanStates.findInBucket("g", key.patternKey, 2, DayType.WEEKDAY, TimeBand.MORNING_PEAK)!!.estimateSec).isEqualTo(40.0)
    }

    @Test
    fun `upsertPrediction replaces the single row, latest wins`() {
        writer.inTransaction { session -> writer.upsertPrediction(session, prediction(Instant.parse("2026-09-04T10:10:00Z"))) }
        writer.inTransaction { session -> writer.upsertPrediction(session, prediction(Instant.parse("2026-09-04T10:20:00Z"))) }
        val all =
            predictions.findByPatternStopAlgorithm(10L, 2, PredictionAlgorithm.KALMAN)
        assertThat(all.size).isEqualTo(1)
        assertThat(all.first().predictedArrivalTs).isEqualTo(Instant.parse("2026-09-04T10:20:00Z"))
    }

    @Test
    fun `deleteOutOfHorizonPredictions drops open rows behind the vehicle and on trips left behind`() {
        val at = Instant.parse("2026-09-04T10:10:00Z")

        fun row(
            tripRowId: Long,
            stopPathIndex: Int,
            actual: Instant? = null,
        ) = prediction(at).copy(tripRowId = tripRowId, stopPathIndex = stopPathIndex, actualArrivalTs = actual)

        writer.inTransaction { session ->
            listOf(
                row(1L, 1, actual = at), // passed and observed: kept as history
                row(1L, 2), // passed but never observed: dropped
                row(1L, 3), // ahead of the vehicle: kept
                row(2L, 0), // next block trip, still in the horizon: kept
                row(9L, 4), // a trip the vehicle is no longer on: dropped
            ).forEach { writer.upsertPrediction(session, it) }
        }

        writer.inTransaction { session ->
            writer.deleteOutOfHorizonPredictions(
                session, feedCode = "f1", vehicleId = "v1", currentTripRowId = 1L, fromStopPathIndex = 3,
                horizonTripRowIds = setOf(1L, 2L),
            )
        }

        val left = predictions.findAll().map { it.tripRowId to it.stopPathIndex }.toSet()
        assertThat(left).isEqualTo(setOf(1L to 1, 1L to 3, 2L to 0))
    }

    @Test
    fun `insertAccuracy appends a row`() {
        writer.inTransaction { session ->
            writer.insertAccuracy(
                session,
                PredictionAccuracy(
                    feedCode = "f1", vehicleId = "v1", tripRowId = 1L, stopPathIndex = 2,
                    algorithm = PredictionAlgorithm.KALMAN,
                    predictedTs = Instant.parse("2026-09-04T10:10:00Z"),
                    actualTs = Instant.parse("2026-09-04T10:12:00Z"),
                    errorSec = 120, absErrorSec = 120,
                    createdAt = Instant.parse("2026-09-04T10:12:00Z"),
                ),
            )
        }
        assertThat(accuracies.count()).isEqualTo(1L)
    }
}
