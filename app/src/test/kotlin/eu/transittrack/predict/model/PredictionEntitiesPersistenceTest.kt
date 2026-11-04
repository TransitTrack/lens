package eu.transittrack.predict.model

import java.time.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import org.jetbrains.exposed.v1.jdbc.Database
import org.springframework.beans.factory.annotation.Autowired

import eu.transittrack.gtfs.support.PostgresSliceTest
import eu.transittrack.predict.PredictionAlgorithm
import eu.transittrack.predict.learn.DayType
import eu.transittrack.predict.learn.TimeBand
import eu.transittrack.support.PostgresPerMethodTest
import eu.transittrack.support.insertAvlFeed

@PostgresSliceTest
class PredictionEntitiesPersistenceTest(
    @Autowired val observations: TravelTimeObservationRepository,
    @Autowired val kalmanStates: KalmanTravelTimeStateRepository,
    @Autowired val predictions: VehiclePredictionRepository,
    @Autowired val accuracies: PredictionAccuracyRepository,
    @Autowired val models: PredictionModelRepository,
    @Autowired val crossings: AvlStopCrossingRepository,
    @Autowired val db: Database,
) : PostgresPerMethodTest() {
    // Committed on its own transaction: the test itself runs outside any Spring transaction
    // (NOT_SUPPORTED).
    @BeforeTest
    fun seedFeed() {
        db.insertAvlFeed(1, "feed-1")
    }

    @Test
    fun `travel_time_observation round-trips`() {
        observations.save(
            TravelTimeObservation(
                gtfsFeedCode = "g", patternKey = "RA|SHP|S1_to_S4|abc", stopPathIndex = 2,
                dayType = DayType.WEEKDAY, timeBand = TimeBand.MORNING_PEAK, sampleCount = 5L, meanSec = 42.5,
                updatedAt = Instant.parse("2026-09-04T10:00:00Z"),
            ),
        )
        val found = observations.findInBucket("g", "RA|SHP|S1_to_S4|abc", 2, DayType.WEEKDAY, TimeBand.MORNING_PEAK)
        assertThat(found).isNotNull()
        assertThat(found!!.meanSec).isEqualTo(42.5)
    }

    @Test
    fun `kalman_travel_time_state round-trips`() {
        kalmanStates.save(
            KalmanTravelTimeState(
                gtfsFeedCode = "g", patternKey = "RA|SHP|S1_to_S4|abc", stopPathIndex = 3,
                dayType = DayType.ALL, timeBand = TimeBand.ALL, estimateSec = 30.0, errorVariance = 400.0,
                sampleCount = 1L, updatedAt = Instant.parse("2026-09-04T10:00:00Z"),
            ),
        )
        val found = kalmanStates.findInBucket("g", "RA|SHP|S1_to_S4|abc", 3, DayType.ALL, TimeBand.ALL)
        assertThat(found).isNotNull()
        assertThat(found!!.estimateSec).isEqualTo(30.0)
    }

    @Test
    fun `vehicle_prediction ordered by stop_path_index`() {
        predictions.save(
            VehiclePrediction(
                feedCode = "feed-1", vehicleId = "v1", tripRowId = 7L, blockPk = null, tripPatternId = 4L,
                stopPathIndex = 3, algorithm = PredictionAlgorithm.KALMAN,
                predictedArrivalTs = Instant.parse("2026-09-04T10:05:00Z"), predictedDepartureTs = null,
                actualArrivalTs = null, actualDepartureTs = null, confidenceSec = 30,
                computedAt = Instant.parse("2026-09-04T10:00:00Z"),
            ),
        )
        predictions.save(
            VehiclePrediction(
                feedCode = "feed-1", vehicleId = "v1", tripRowId = 7L, blockPk = null, tripPatternId = 4L,
                stopPathIndex = 1, algorithm = PredictionAlgorithm.KALMAN,
                predictedArrivalTs = Instant.parse("2026-09-04T10:01:00Z"), predictedDepartureTs = null,
                actualArrivalTs = null, actualDepartureTs = null, confidenceSec = 30,
                computedAt = Instant.parse("2026-09-04T10:00:00Z"),
            ),
        )
        val ordered = predictions.findByVehicleTrip("feed-1", "v1", 7L)
        assertThat(ordered).hasSize(2)
        assertThat(ordered.first().stopPathIndex).isEqualTo(1)
        assertThat(ordered.last().stopPathIndex).isEqualTo(3)

        val byPattern = predictions.findByPatternStopAlgorithm("feed-1", 4L, 1, PredictionAlgorithm.KALMAN)
        assertThat(byPattern).hasSize(1)
    }

    @Test
    fun `prediction_accuracy filters by created_at`() {
        accuracies.save(
            PredictionAccuracy(
                feedCode = "feed-1", vehicleId = "v2", tripRowId = 8L, stopPathIndex = 1,
                algorithm = PredictionAlgorithm.HISTORICAL_AVERAGE,
                predictedTs = Instant.parse("2026-09-04T10:00:00Z"),
                actualTs = Instant.parse("2026-09-04T10:00:10Z"),
                errorSec = 10, absErrorSec = 10,
                createdAt = Instant.parse("2026-09-04T09:00:00Z"),
            ),
        )
        accuracies.save(
            PredictionAccuracy(
                feedCode = "feed-1", vehicleId = "v2", tripRowId = 8L, stopPathIndex = 1,
                algorithm = PredictionAlgorithm.HISTORICAL_AVERAGE,
                predictedTs = Instant.parse("2026-09-04T11:00:00Z"),
                actualTs = Instant.parse("2026-09-04T11:00:05Z"),
                errorSec = 5, absErrorSec = 5,
                createdAt = Instant.parse("2026-09-04T11:00:00Z"),
            ),
        )
        val recent = accuracies.findRecentByFeedAndAlgorithm(
            "feed-1", PredictionAlgorithm.HISTORICAL_AVERAGE, Instant.parse("2026-09-04T10:00:00Z"),
        )
        assertThat(recent).hasSize(1)
        assertThat(recent.first().errorSec).isEqualTo(5)
    }

    /** No algorithm to compare is nothing to aggregate: no `algorithm in ()` query is sent. */
    @Test
    fun `aggregateAccuracy without algorithms returns nothing`() {
        assertThat(
            accuracies.aggregateAccuracy("feed-1", emptyList(), Instant.EPOCH, null, null, null, null),
        ).isEqualTo(emptyList())
    }

    @Test
    fun `range and lateness round-trip on prediction and accuracy`() {
        predictions.save(
            VehiclePrediction(
                feedCode = "feed-1", vehicleId = "v3", tripRowId = 9L, blockPk = null, tripPatternId = 4L,
                stopPathIndex = 1, algorithm = PredictionAlgorithm.SMILE_GBM,
                predictedArrivalTs = Instant.parse("2026-09-04T10:01:00Z"), predictedDepartureTs = null,
                actualArrivalTs = null, actualDepartureTs = null, confidenceSec = 30,
                computedAt = Instant.parse("2026-09-04T10:00:00Z"),
                rangeLowSec = -40, rangeHighSec = 90, lateProbability = 0.25, late = false,
            ),
        )
        val p = predictions.findByVehicleTrip("feed-1", "v3", 9L).single()
        assertThat(p.rangeLowSec).isEqualTo(-40)
        assertThat(p.rangeHighSec).isEqualTo(90)
        assertThat(p.lateProbability).isEqualTo(0.25)
        assertThat(p.late).isEqualTo(false)

        accuracies.save(
            PredictionAccuracy(
                feedCode = "feed-1", vehicleId = "v3", tripRowId = 9L, stopPathIndex = 1,
                algorithm = PredictionAlgorithm.SMILE_GBM,
                predictedTs = Instant.parse("2026-09-04T10:00:00Z"),
                actualTs = Instant.parse("2026-09-04T10:00:10Z"),
                errorSec = 10, absErrorSec = 10,
                createdAt = Instant.parse("2026-09-04T09:00:00Z"),
                rangeLowSec = -5, rangeHighSec = 15, predictedLate = false, actualLate = true,
            ),
        )
        val a = accuracies.findAll().single { it.vehicleId == "v3" }
        assertThat(a.rangeLowSec).isEqualTo(-5)
        assertThat(a.rangeHighSec).isEqualTo(15)
        assertThat(a.predictedLate).isEqualTo(false)
        assertThat(a.actualLate).isEqualTo(true)
    }

    @Test
    fun `prediction_model upserts and reads back`() {
        assertThat(models.find("feed-1", PredictionAlgorithm.SMILE_GBM)).isNull()
        assertThat(models.trainedAt("feed-1", PredictionAlgorithm.SMILE_GBM)).isNull()
        val t1 = Instant.parse("2026-09-04T10:00:00Z")
        models.save(PredictionModelRow("feed-1", PredictionAlgorithm.SMILE_GBM, t1, 10, byteArrayOf(1, 2, 3)))
        val t2 = Instant.parse("2026-09-05T10:00:00Z")
        models.save(PredictionModelRow("feed-1", PredictionAlgorithm.SMILE_GBM, t2, 20, byteArrayOf(9)))
        val found = models.find("feed-1", PredictionAlgorithm.SMILE_GBM)
        assertThat(found).isEqualTo(PredictionModelRow("feed-1", PredictionAlgorithm.SMILE_GBM, t2, 20, byteArrayOf(9)))
        assertThat(models.trainedAt("feed-1", PredictionAlgorithm.SMILE_GBM)).isEqualTo(t2)
    }

    private fun crossing(
        at: String,
        travel: Double?,
        gap: Int?,
    ) = AvlStopCrossing(
        feedCode = "feed-1", vehicleId = "v1", tripRowId = 1L, tripPatternId = 1L, stopPathIndex = 1,
        observedAt = Instant.parse(at), observedTravelTimeSec = travel, serviceId = null, tripStartSec = null,
        routeId = null, directionId = null, revisionId = 1L, reportGapSec = gap,
    )

    @Test
    fun `training returns newest rows within limit`() {
        crossings.save(crossing("2026-09-01T10:00:00Z", 30.0, 10))
        crossings.save(crossing("2026-09-02T10:00:00Z", 31.0, 10))
        crossings.save(crossing("2026-09-03T10:00:00Z", 32.0, 10))
        val rows = crossings.training("feed-1", Instant.parse("2026-08-01T00:00:00Z"), 60, 600, 2)
        assertThat(travelSecs(rows)).isEqualTo(listOf(32.0, 31.0))
    }

    @Test
    fun `training skips implausible and unreliable rows`() {
        crossings.save(crossing("2026-09-01T10:00:00Z", 30.0, 10))
        crossings.save(crossing("2026-09-01T11:00:00Z", 30.5, null))
        crossings.save(crossing("2026-09-01T12:00:00Z", null, 10))
        crossings.save(crossing("2026-09-01T13:00:00Z", 9999.0, 10))
        crossings.save(crossing("2026-09-01T14:00:00Z", 30.0, 500))
        crossings.save(crossing("2026-07-01T10:00:00Z", 30.0, 10))
        val rows = crossings.training("feed-1", Instant.parse("2026-08-01T00:00:00Z"), 60, 600, 100)
        assertThat(travelSecs(rows)).isEqualTo(listOf(30.5, 30.0))
    }

    @Test
    fun `training reads the columns training uses, nulls included`() {
        crossings.save(
            crossing("2026-09-02T10:00:00Z", 45.0, 10).apply {
                tripPatternId = 7L
                revisionId = 3L
                stopPathIndex = 4
                tripStartSec = 25_200
                directionId = 1
                routeId = "R1"
            },
        )
        crossings.save(crossing("2026-09-01T10:00:00Z", 30.0, 10).apply { routeId = "R1" })
        crossings.save(crossing("2026-08-31T10:00:00Z", 20.0, 10))

        val rows = crossings.training("feed-1", Instant.parse("2026-08-01T00:00:00Z"), 60, 600, 100)

        assertThat(rows.size).isEqualTo(3)
        assertThat(rows.tripPatternId(0)).isEqualTo(7L)
        assertThat(rows.revisionId(0)).isEqualTo(3L)
        assertThat(rows.stopPathIndex(0)).isEqualTo(4)
        assertThat(rows.observedAt(0)).isEqualTo(Instant.parse("2026-09-02T10:00:00Z"))
        assertThat(rows.travelSec(0)).isEqualTo(45.0)
        assertThat(rows.tripStartSec(0)).isEqualTo(25_200)
        assertThat(rows.directionId(0)).isEqualTo(1)
        assertThat(rows.routeId(0)).isEqualTo("R1")
        assertThat(rows.routeId(1)).isEqualTo("R1")
        assertThat(rows.routeId(2)).isNull()
        assertThat(rows.tripStartSec(2)).isNull()
        assertThat(rows.directionId(2)).isNull()
    }

    private fun travelSecs(rows: TrainingCrossings) = (0 until rows.size).map { rows.travelSec(it) }
}
