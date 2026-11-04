package eu.transittrack.predict

import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired

import eu.transittrack.gtfs.support.PostgresSliceTest
import eu.transittrack.predict.learn.DayType
import eu.transittrack.predict.learn.TimeBand
import eu.transittrack.predict.model.AvlStopCrossingTable
import eu.transittrack.predict.model.KalmanTravelTimeStateTable
import eu.transittrack.predict.model.PredictionAccuracyTable
import eu.transittrack.predict.model.PredictionCheckpointTable
import eu.transittrack.predict.model.PredictionFrequencyRunTable
import eu.transittrack.predict.model.TravelTimeObservationTable
import eu.transittrack.predict.model.VehiclePredictionTable
import eu.transittrack.schedule.model.TripPatternTable
import eu.transittrack.support.PostgresPerMethodTest
import eu.transittrack.support.insertAvlFeed
import eu.transittrack.support.insertGtfsFeed
import eu.transittrack.support.insertGtfsRevision
import eu.transittrack.util.observability.TransitTrackMetrics

@PostgresSliceTest
class PredictionRetentionSchedulerTest(
    @Autowired val db: Database,
    @Autowired val retention: PredictionRetentionRepository,
) : PostgresPerMethodTest() {
    private val now = Instant.parse("2026-09-05T12:00:00Z")
    private lateinit var scheduler: PredictionRetentionScheduler

    @BeforeEach
    fun seed() {
        scheduler = PredictionRetentionScheduler(retention, PredictProperties(), TransitTrackMetrics.forTests())

        db.insertGtfsFeed(1, "g")
        db.insertGtfsRevision(1, 1)
        db.insertAvlFeed(1, "f1", name = "F1", gtfsFeedCode = "g")

        transaction(db) {
            TripPatternTable.insert {
                it[id] = 100L
                it[revisionId] = 1L
                it[patternKey] = "k"
                it[routeId] = "r"
                it[stopCount] = 1
                it[tripCount] = 1
            }

            // Learned rows are keyed by (gtfs feed code, pattern_key): one surviving (the pattern still
            // exists in a stored revision of feed g), one whose key no revision has any more, and one
            // with the right key but under another feed - both orphans.
            for ((feedCode, patternKey) in listOf("g" to "k", "g" to "gone", "other" to "k")) {
                TravelTimeObservationTable.insert {
                    it[gtfsFeedCode] = feedCode
                    it[TravelTimeObservationTable.patternKey] = patternKey
                    it[stopPathIndex] = 0
                    it[dayType] = DayType.ALL
                    it[timeBand] = TimeBand.ALL
                    it[sampleCount] = 1L
                    it[meanSec] = 30.0
                    it[updatedAt] = now
                }
                KalmanTravelTimeStateTable.insert {
                    it[gtfsFeedCode] = feedCode
                    it[KalmanTravelTimeStateTable.patternKey] = patternKey
                    it[stopPathIndex] = 0
                    it[dayType] = DayType.ALL
                    it[timeBand] = TimeBand.ALL
                    it[estimateSec] = 30.0
                    it[errorVariance] = 5.0
                    it[sampleCount] = 1L
                    it[updatedAt] = now
                }
            }

            // vehicle_prediction: one old (deleted), one young (kept)
            for ((vehicle, tripRow, age) in listOf(Triple("v1", 1L, 10L), Triple("v2", 2L, 1L))) {
                VehiclePredictionTable.insert {
                    it[feedCode] = "f1"
                    it[vehicleId] = vehicle
                    it[tripRowId] = tripRow
                    it[tripPatternId] = 100L
                    it[stopPathIndex] = 0
                    it[algorithm] = PredictionAlgorithm.SCHEDULE_ADHERENCE
                    it[computedAt] = now.minus(age, ChronoUnit.HOURS)
                }
            }

            // prediction_checkpoint: one never scored within a prediction's lifetime (deleted), one young (kept)
            for ((vehicle, age) in listOf("v1" to 10L, "v2" to 1L)) {
                val ts = now.minus(age, ChronoUnit.HOURS)
                PredictionCheckpointTable.insert {
                    it[feedCode] = "f1"
                    it[vehicleId] = vehicle
                    it[tripRowId] = 1L
                    it[stopPathIndex] = 0
                    it[algorithm] = PredictionAlgorithm.SCHEDULE_ADHERENCE
                    it[sampleSlot] = ts
                    it[predictedTs] = ts
                    it[computedAt] = ts
                    it[producedAt] = ts
                }
                PredictionFrequencyRunTable.insert {
                    it[feedCode] = "f1"
                    it[vehicleId] = vehicle
                    it[tripRowId] = 1L
                    it[serviceDate] = LocalDate.of(2026, 9, 5)
                    it[runStartedAt] = ts
                    it[lastReportTs] = ts
                }
            }

            // prediction_accuracy: one old (deleted), one young (kept)
            for (
            (vehicle, tripRow, ts) in
            listOf(
                Triple("v1", 1L, now.minus(40, ChronoUnit.DAYS)),
                Triple("v2", 2L, now.minus(1, ChronoUnit.DAYS)),
            )
            ) {
                PredictionAccuracyTable.insert {
                    it[feedCode] = "f1"
                    it[vehicleId] = vehicle
                    it[tripRowId] = tripRow
                    it[stopPathIndex] = 0
                    it[algorithm] = PredictionAlgorithm.SCHEDULE_ADHERENCE
                    it[predictedTs] = ts
                    it[actualTs] = ts
                    it[errorSec] = 10
                    it[absErrorSec] = 10
                    it[createdAt] = ts
                }
            }

            for ((vehicle, tripRow, age) in listOf(Triple("v1", 1L, 100L), Triple("v2", 2L, 1L))) {
                AvlStopCrossingTable.insert {
                    it[feedCode] = "f1"
                    it[vehicleId] = vehicle
                    it[tripRowId] = tripRow
                    it[tripPatternId] = 100L
                    it[stopPathIndex] = 0
                    it[observedAt] = now.minus(age, ChronoUnit.DAYS)
                    it[observedTravelTimeSec] = 30.0
                    it[revisionId] = 1L
                }
            }
        }
    }

    private fun vehicleIds(
        table: Table,
        vehicleId: Column<String>,
    ): List<String> = transaction(db) { table.select(vehicleId).map { it[vehicleId] } }

    @Test
    fun `prunes aged rows and sweeps orphan learner rows, keeping real-pattern and young rows`() {
        val counts = scheduler.prune(now)

        assertThat(counts).isEqualTo(
            PredictionRetentionScheduler.PruneCounts(
                predictions = 1, checkpoints = 1, accuracy = 1, crossings = 1, orphanObservations = 2, orphanKalman = 2,
                frequencyRuns = 1,
            ),
        )

        assertThat(
            transaction(db) {
                TravelTimeObservationTable.selectAll().map {
                    "${it[TravelTimeObservationTable.gtfsFeedCode]}/${it[TravelTimeObservationTable.patternKey]}"
                }
            },
        ).containsExactly("g/k")
        assertThat(
            transaction(db) {
                KalmanTravelTimeStateTable.selectAll().map {
                    "${it[KalmanTravelTimeStateTable.gtfsFeedCode]}/${it[KalmanTravelTimeStateTable.patternKey]}"
                }
            },
        ).containsExactly("g/k")
        assertThat(
            vehicleIds(VehiclePredictionTable, VehiclePredictionTable.vehicleId),
        ).containsExactly("v2")
        assertThat(
            vehicleIds(PredictionFrequencyRunTable, PredictionFrequencyRunTable.vehicleId),
        ).containsExactly("v2")
        assertThat(
            vehicleIds(PredictionCheckpointTable, PredictionCheckpointTable.vehicleId),
        ).containsExactly("v2")
        assertThat(
            vehicleIds(PredictionAccuracyTable, PredictionAccuracyTable.vehicleId),
        ).containsExactly("v2")
        assertThat(
            vehicleIds(AvlStopCrossingTable, AvlStopCrossingTable.vehicleId),
        ).containsExactly("v2")
    }
}
