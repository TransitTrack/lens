package eu.transittrack.predict

import java.time.Instant
import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

import eu.transittrack.AvlAssignmentMode
import eu.transittrack.AvlFormat
import eu.transittrack.avl.model.AvlFeed
import eu.transittrack.avl.model.AvlFeedRepository
import eu.transittrack.avl.model.AvlFeedSourceKind
import eu.transittrack.gtfs.support.PostgresSliceTest
import eu.transittrack.predict.learn.DayType
import eu.transittrack.predict.learn.KalmanState
import eu.transittrack.predict.learn.LearningKey
import eu.transittrack.predict.learn.RunningAverageState
import eu.transittrack.predict.learn.TimeBand
import eu.transittrack.predict.learn.TravelTimeBucket
import eu.transittrack.predict.model.AvlStopCrossing
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
    @Autowired val jdbc: JdbcTemplate,
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
        jdbc.update("delete from prediction_checkpoint")
        jdbc.update("delete from avl_stop_crossing")
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

    private fun upsert(
        session: org.hibernate.StatelessSession,
        vararg rows: VehiclePredictionUpsert,
        now: Instant = rows.first().computedAt,
    ) = writer.upsertPredictions(
        session,
        rows.toList(),
        staleBefore = now.minusSeconds(6 * 3_600),
        refreshBefore = now.minusSeconds(120),
        minChangeSec = 10,
    )

    private fun observation(
        sampleCount: Long,
        meanSec: Double,
    ) = RunningAverageState(sampleCount, meanSec)

    @Test
    fun `upsertObservations replaces the single row per bucket, latest wins`() {
        writer.inTransaction { session ->
            writer
                .upsertObservations(
                    session, key, mapOf(LearnerBucket(2, peak) to observation(1L, 30.0)),
                    Instant
                        .parse("2026-09-04T10:00:00Z"),
                )
        }
        writer.inTransaction { session ->
            writer
                .upsertObservations(
                    session, key, mapOf(LearnerBucket(2, peak) to observation(2L, 45.0)),
                    Instant
                        .parse("2026-09-04T10:05:00Z"),
                )
        }
        assertThat(observations.findAll().size).isEqualTo(1)
        assertThat(observations.findInBucket("g", key.patternKey, 2, DayType.WEEKDAY, TimeBand.MORNING_PEAK)!!.meanSec).isEqualTo(45.0)
    }

    @Test
    fun `upsertObservations keeps buckets of the same stop path apart`() {
        writer.inTransaction { session ->
            writer.upsertObservations(
                session,
                key,
                mapOf(LearnerBucket(2, peak) to observation(1L, 90.0), LearnerBucket(2, TravelTimeBucket.OVERALL) to observation(1L, 60.0)),
                Instant.parse("2026-09-04T08:00:00Z"),
            )
        }
        assertThat(observations.findByPattern("g", key.patternKey).associate { it.timeBand to it.meanSec })
            .isEqualTo(mapOf(TimeBand.MORNING_PEAK to 90.0, TimeBand.ALL to 60.0))
    }

    @Test
    fun `upsertKalmanStates replaces the single row per bucket, latest wins`() {
        writer.inTransaction { session ->
            writer
                .upsertKalmanStates(
                    session, key, mapOf(LearnerBucket(2, peak) to KalmanState(30.0, 5.0, 1L)),
                    Instant
                        .parse("2026-09-04T10:00:00Z"),
                )
        }
        writer.inTransaction { session ->
            writer
                .upsertKalmanStates(
                    session, key, mapOf(LearnerBucket(2, peak) to KalmanState(40.0, 3.0, 2L)),
                    Instant
                        .parse("2026-09-04T10:05:00Z"),
                )
        }
        assertThat(kalmanStates.findAll().size).isEqualTo(1)
        assertThat(kalmanStates.findInBucket("g", key.patternKey, 2, DayType.WEEKDAY, TimeBand.MORNING_PEAK)!!.estimateSec).isEqualTo(40.0)
    }

    @Test
    fun `upsertPredictions replaces the single row, latest wins`() {
        writer.inTransaction { session -> upsert(session, prediction(Instant.parse("2026-09-04T10:10:00Z"))) }
        writer.inTransaction { session -> upsert(session, prediction(Instant.parse("2026-09-04T10:20:00Z"))) }
        val all =
            predictions.findByPatternStopAlgorithm("f1", 10L, 2, PredictionAlgorithm.KALMAN)
        assertThat(all.size).isEqualTo(1)
        assertThat(all.first().predictedArrivalTs).isEqualTo(Instant.parse("2026-09-04T10:20:00Z"))
    }

    @Test
    fun `upsertPredictions leaves a barely changed row alone until it is due for a refresh`() {
        val t0 = Instant.parse("2026-09-04T10:00:00Z")
        val arrival = Instant.parse("2026-09-04T10:10:00Z")

        fun at(
            computedAt: Instant,
            predicted: Instant,
        ) = prediction(predicted).copy(computedAt = computedAt)

        fun stored() = predictions.findAll().single().let { it.predictedArrivalTs to it.computedAt }

        writer.inTransaction { session -> upsert(session, at(t0, arrival)) }
        // Moved by 5s: under the 10s threshold, kept as it was.
        writer.inTransaction { session -> upsert(session, at(t0.plusSeconds(30), arrival.plusSeconds(5))) }
        assertThat(stored()).isEqualTo(arrival to t0)
        // Moved by 15s: rewritten.
        writer.inTransaction { session -> upsert(session, at(t0.plusSeconds(60), arrival.plusSeconds(15))) }
        assertThat(stored()).isEqualTo(arrival.plusSeconds(15) to t0.plusSeconds(60))
        // Barely changed, but older than the refresh interval: rewritten so it still reads as live.
        writer.inTransaction { session -> upsert(session, at(t0.plusSeconds(200), arrival.plusSeconds(18))) }
        assertThat(stored()).isEqualTo(arrival.plusSeconds(18) to t0.plusSeconds(200))
    }

    @Test
    fun `upsertPredictions keeps a passed stop's row, unless it is from an earlier run`() {
        val t0 = Instant.parse("2026-09-04T10:00:00Z")
        val actual = Instant.parse("2026-09-04T10:12:00Z")
        writer.inTransaction { session ->
            upsert(session, prediction(Instant.parse("2026-09-04T10:10:00Z")))
            writer.fillActuals(session, "f1", "v1", 1L, mapOf(2 to actual))
        }
        // GPS jitter puts the stop back in the horizon a minute later: the passed row stays.
        writer.inTransaction { session ->
            upsert(session, prediction(Instant.parse("2026-09-04T10:13:00Z")).copy(computedAt = t0.plusSeconds(60)))
        }
        predictions.findAll().single().let {
            assertThat(it.actualArrivalTs).isEqualTo(actual)
            assertThat(it.predictedArrivalTs).isEqualTo(Instant.parse("2026-09-04T10:10:00Z"))
        }
        // The next day the same trip row starts afresh.
        val nextDay = t0.plusSeconds(86_400)
        writer.inTransaction { session -> upsert(session, prediction(nextDay.plusSeconds(600)).copy(computedAt = nextDay)) }
        predictions.findAll().single().let {
            assertThat(it.actualArrivalTs).isNull()
            assertThat(it.predictedArrivalTs).isEqualTo(nextDay.plusSeconds(600))
        }
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
            ).let { upsert(session, *it.toTypedArray()) }
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
                listOf(
                    PredictionAccuracy(
                        feedCode = "f1", vehicleId = "v1", tripRowId = 1L, stopPathIndex = 2,
                        algorithm = PredictionAlgorithm.KALMAN,
                        predictedTs = Instant.parse("2026-09-04T10:10:00Z"),
                        actualTs = Instant.parse("2026-09-04T10:12:00Z"),
                        errorSec = 120, absErrorSec = 120,
                        createdAt = Instant.parse("2026-09-04T10:12:00Z"),
                    ),
                ),
            )
        }
        assertThat(accuracies.count()).isEqualTo(1L)
    }

    private fun checkpoint(
        horizonSec: Int,
        predictedTs: Instant,
        computedAt: Instant,
    ) = CheckpointCandidate(
        feedCode = "f1", vehicleId = "v1", tripRowId = 1L, stopPathIndex = 2, algorithm = PredictionAlgorithm.KALMAN,
        horizonSec = horizonSec, predictedTs = predictedTs, computedAt = computedAt,
    )

    @Test
    fun `insertCheckpoints keeps the first prediction per horizon and replaces a stale one`() {
        val t0 = Instant.parse("2026-09-04T10:00:00Z")
        writer.inTransaction { session ->
            writer.insertCheckpoints(session, listOf(checkpoint(300, t0.plusSeconds(280), t0)), staleBefore = t0.minusSeconds(3_600))
        }
        writer.inTransaction { session ->
            writer.insertCheckpoints(
                session,
                listOf(checkpoint(300, t0.plusSeconds(330), t0.plusSeconds(30)), checkpoint(180, t0.plusSeconds(200), t0.plusSeconds(30))),
                staleBefore = t0.minusSeconds(3_600),
            )
        }
        var taken = emptyList<eu.transittrack.predict.model.PredictionCheckpoint>()
        writer.inTransaction { session -> taken = writer.takeCheckpoints(session, "f1", "v1", 1L, listOf(2)) }
        assertThat(taken.associate { it.horizonSec to it.predictedTs })
            .isEqualTo(mapOf(300 to t0.plusSeconds(280), 180 to t0.plusSeconds(200)))
        writer.inTransaction { session -> assertThat(writer.takeCheckpoints(session, "f1", "v1", 1L, listOf(2))).isEmpty() }

        // A day later the same trip row's leftover checkpoint is stale and gets replaced.
        val nextDay = t0.plusSeconds(86_400)
        writer.inTransaction { session ->
            writer.insertCheckpoints(session, listOf(checkpoint(300, t0.plusSeconds(280), t0)), staleBefore = t0.minusSeconds(3_600))
            writer.insertCheckpoints(
                session,
                listOf(checkpoint(300, nextDay.plusSeconds(290), nextDay)),
                staleBefore = nextDay.minusSeconds(3_600),
            )
            assertThat(writer.takeCheckpoints(session, "f1", "v1", 1L, listOf(2)).single().computedAt).isEqualTo(nextDay)
        }
    }

    @Test
    fun `insertCheckpoints skips a stop the vehicle already passed`() {
        val t0 = Instant.parse("2026-09-04T10:00:00Z")
        writer.inTransaction { session ->
            upsert(session, prediction(t0.plusSeconds(280)))
            writer.fillActuals(session, "f1", "v1", 1L, mapOf(2 to t0.plusSeconds(290)))
            writer.insertCheckpoints(session, listOf(checkpoint(300, t0.plusSeconds(280), t0)), staleBefore = t0.minusSeconds(3_600))
            assertThat(writer.takeCheckpoints(session, "f1", "v1", 1L, listOf(2))).isEmpty()
        }
    }

    @Test
    fun `clearPreviousRun drops the passed predictions and leftover checkpoints of the trip row`() {
        val t0 = Instant.parse("2026-09-04T10:00:00Z")
        writer.inTransaction { session ->
            upsert(session, prediction(t0.plusSeconds(280)), prediction(t0.plusSeconds(400)).copy(stopPathIndex = 3))
            writer.fillActuals(session, "f1", "v1", 1L, mapOf(2 to t0.plusSeconds(290)))
            writer.insertCheckpoints(
                session,
                listOf(checkpoint(600, t0.plusSeconds(400), t0).copy(stopPathIndex = 3)),
                staleBefore = t0.minusSeconds(3_600),
            )
            writer.clearPreviousRun(session, "f1", "v1", 1L)
            assertThat(writer.takeCheckpoints(session, "f1", "v1", 1L, listOf(2, 3))).isEmpty()
        }
        assertThat(predictions.findAll().map { it.stopPathIndex }).isEqualTo(listOf(3))
    }

    @Test
    fun `learner rows are read back, and locked, in the writing transaction`() {
        val at = Instant.parse("2026-09-04T10:00:00Z")
        val fine = LearnerBucket(2, peak)
        val overall = LearnerBucket(2, TravelTimeBucket.OVERALL)
        val nextStop = LearnerBucket(3, peak)
        writer.inTransaction { session ->
            assertThat(writer.lockObservations(session, key, listOf(fine, overall))).isEmpty()
            writer.upsertObservations(session, key, mapOf(fine to observation(3L, 42.0), nextStop to observation(1L, 70.0)), at)
            writer.upsertKalmanStates(session, key, mapOf(fine to KalmanState(40.0, 90.0, 3L)), at)
            assertThat(writer.lockObservations(session, key, listOf(fine, overall, nextStop))).isEqualTo(
                mapOf(fine to observation(3L, 42.0), nextStop to observation(1L, 70.0)),
            )
            assertThat(writer.lockKalmanStates(session, key, listOf(fine, overall))).isEqualTo(mapOf(fine to KalmanState(40.0, 90.0, 3L)))
        }
    }

    @Test
    fun `lastPasses returns each stop's latest pass since the cut-off`() {
        val t0 = Instant.parse("2026-09-04T10:00:00Z")

        fun pass(
            k: Int,
            at: Instant,
        ) = AvlStopCrossing(
            feedCode = "f1", vehicleId = "v1", tripRowId = 1L, tripPatternId = 10L, stopPathIndex = k, observedAt = at,
            observedTravelTimeSec = null, serviceId = null, tripStartSec = null, routeId = null, directionId = null, revisionId = 1L,
        )
        writer.inTransaction { session ->
            writer.insertCrossings(
                session,
                listOf(pass(1, t0.minusSeconds(86_400)), pass(1, t0), pass(2, t0.plusSeconds(60)), pass(3, t0.minusSeconds(86_400))),
            )
            assertThat(writer.lastPasses(session, "f1", "v1", 1L, since = t0.minusSeconds(1_800)))
                .isEqualTo(mapOf(1 to t0, 2 to t0.plusSeconds(60)))
        }
    }

    @Test
    fun `fillActuals sets the arrival, or the departure of a first stop, on every algorithm's row`() {
        val actual = Instant.parse("2026-09-04T10:12:00Z")
        val departed = Instant.parse("2026-09-04T10:01:00Z")
        writer.inTransaction { session ->
            upsert(
                session,
                prediction(Instant.parse("2026-09-04T10:10:00Z")),
                prediction(Instant.parse("2026-09-04T10:11:00Z")).copy(algorithm = PredictionAlgorithm.SCHEDULE_ADHERENCE),
                prediction(Instant.parse("2026-09-04T10:00:00Z")).copy(stopPathIndex = 0),
            )
            writer.fillActuals(session, "f1", "v1", 1L, mapOf(0 to departed, 2 to actual))
        }
        val rows = predictions.findAll().groupBy { it.stopPathIndex }
        assertThat(rows.getValue(2).map { it.actualArrivalTs }.toSet()).isEqualTo(setOf<Instant?>(actual))
        assertThat(rows.getValue(2).map { it.actualDepartureTs }.toSet()).isEqualTo(setOf<Instant?>(null))
        assertThat(rows.getValue(0).single().actualDepartureTs).isEqualTo(departed)
        assertThat(rows.getValue(0).single().actualArrivalTs).isNull()
    }
}
