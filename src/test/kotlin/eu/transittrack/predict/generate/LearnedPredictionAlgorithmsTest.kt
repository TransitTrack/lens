package eu.transittrack.predict.generate

import java.time.Instant
import java.time.LocalDate
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary

import eu.transittrack.TestcontainersConfiguration
import eu.transittrack.avl.AvlAssignmentMode
import eu.transittrack.avl.AvlFormat
import eu.transittrack.avl.match.AvlMatchContextFactory
import eu.transittrack.avl.model.AvlFeed
import eu.transittrack.avl.model.AvlFeedSourceKind
import eu.transittrack.gtfs.download.FeedDownloader
import eu.transittrack.gtfs.feed.FeedInput
import eu.transittrack.gtfs.feed.GtfsFeedService
import eu.transittrack.gtfs.ingest.IngestionService
import eu.transittrack.gtfs.model.TripRepository
import eu.transittrack.gtfs.support.FixtureDownloader
import eu.transittrack.predict.model.KalmanTravelTimeState
import eu.transittrack.predict.model.KalmanTravelTimeStateRepository
import eu.transittrack.predict.model.TravelTimeObservation
import eu.transittrack.predict.model.TravelTimeObservationRepository
import eu.transittrack.schedule.model.TravelTimesForStopPathRepository

@SpringBootTest(classes = [eu.transittrack.Application::class])
@Import(TestcontainersConfiguration::class, LearnedPredictionAlgorithmsTest.Stub::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class LearnedPredictionAlgorithmsTest(
    @Autowired val factory: AvlMatchContextFactory,
    @Autowired val feedService: GtfsFeedService,
    @Autowired val ingestion: IngestionService,
    @Autowired val trips: TripRepository,
    @Autowired val observations: TravelTimeObservationRepository,
    @Autowired val states: KalmanTravelTimeStateRepository,
    @Autowired val seeds: TravelTimesForStopPathRepository,
) {
    @TestConfiguration(proxyBeanMethods = false)
    class Stub {
        @Bean
        @Primary
        fun dl(): FeedDownloader = FixtureDownloader("schedule-sample")
    }

    @BeforeAll
    fun ingest() {
        feedService.register(FeedInput("g", "G", null, "http://x/g.zip", null))
        ingestion.ingestBlocking("g")
    }

    private val avlFeed =
        AvlFeed(
            code = "a",
            name = "A",
            gtfsFeedCode = "g",
            url = "x",
            format = AvlFormat.GTFS_RT,
            pollIntervalSec = 15,
            assignmentMode = AvlAssignmentMode.TRUST_DESCRIPTOR,
            enabled = true,
            headers = null,
            source = AvlFeedSourceKind.CONFIG,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )

    private val serviceDate: LocalDate = LocalDate.of(2026, 9, 7)

    /**
     * Recomputes, straight from the seed repository, the same accumulation the algorithms
     * perform under the schedule-seed fallback: anchor at the first horizon stop's own scheduled
     * time, then walk forward adding each stop's seeded travel + dwell time.
     */
    private fun expectedSeedArrivals(
        horizon: List<HorizonStop>,
        ctx: eu.transittrack.avl.match.AvlMatchContext,
    ): Map<Int, Instant> {
        val first = horizon.first()
        val anchorSchedule = ctx.scheduleOf(first.tripRowId).first { it.stopPathIndex == first.stopPathIndex }
        val anchorSec = anchorSchedule.arrivalSec ?: anchorSchedule.departureSec!!
        var lastTs = serviceSecToInstant(serviceDate, anchorSec, ctx.zone)
        val result = LinkedHashMap<Int, Instant>()
        for (stop in horizon) {
            val row =
                seeds.findByTripPatternOrdered(ctx.revisionId, stop.tripPatternId).first { it.stopPathIndex == stop.stopPathIndex }
            val travelSec = row.travelTimeSec!!
            lastTs = lastTs.plusSeconds(travelSec.toLong())
            result[stop.stopPathIndex] = lastTs
            val dwellSec = row.dwellTimeSec ?: 0
            lastTs = lastTs.plusSeconds(dwellSec.toLong())
        }
        return result
    }

    @Test
    @Order(1)
    fun `HistoricalAverageAlgorithm falls back to schedule seed with no observations`() {
        val ctx = factory.open(avlFeed)!!
        val trip = trips.findByTripId(ctx.revisionId, "T1")!!
        val horizon = buildHorizon(trip.id!!, trip.tripPatternId!!, fromStopPathIndex = 1, ctx).filter { it.tripRowId == trip.id }
        assertThat(horizon.size).isGreaterThan(0)
        val expected = expectedSeedArrivals(horizon, ctx)

        val algorithm = HistoricalAverageAlgorithm(observations, seeds)
        val predictions = algorithm.predict(horizon, serviceDate, adherenceSec = 0, ctx)

        for (stop in horizon) {
            val prediction = predictions.first { it.stopPathIndex == stop.stopPathIndex && it.tripRowId == stop.tripRowId }
            assertThat(prediction.predictedArrivalTs).isEqualTo(expected[stop.stopPathIndex])
        }
    }

    @Test
    @Order(2)
    fun `HistoricalAverageAlgorithm uses learned observation over the schedule seed`() {
        val ctx = factory.open(avlFeed)!!
        val trip = trips.findByTripId(ctx.revisionId, "T1")!!
        val horizon = buildHorizon(trip.id!!, trip.tripPatternId!!, fromStopPathIndex = 1, ctx).filter { it.tripRowId == trip.id }
        assertThat(horizon.size).isGreaterThan(0)
        val target = horizon.first()

        observations.save(
            TravelTimeObservation(
                tripPatternId = target.tripPatternId,
                stopPathIndex = target.stopPathIndex,
                sampleCount = 5,
                meanSec = 999.0,
                updatedAt = Instant.now(),
            ),
        )

        val algorithm = HistoricalAverageAlgorithm(observations, seeds)
        val predictions = algorithm.predict(horizon, serviceDate, adherenceSec = 0, ctx)
        val prediction = predictions.first { it.stopPathIndex == target.stopPathIndex && it.tripRowId == target.tripRowId }

        val anchorSchedule = ctx.scheduleOf(target.tripRowId).first { it.stopPathIndex == target.stopPathIndex }
        val anchorSec = anchorSchedule.arrivalSec ?: anchorSchedule.departureSec!!
        val anchorTs = serviceSecToInstant(serviceDate, anchorSec, ctx.zone)

        assertThat(prediction.predictedArrivalTs).isEqualTo(anchorTs.plusSeconds(999L))
    }

    @Test
    @Order(3)
    fun `KalmanAlgorithm falls back to schedule seed with no state and reports null confidence`() {
        val ctx = factory.open(avlFeed)!!
        val trip = trips.findByTripId(ctx.revisionId, "T1")!!
        val horizon = buildHorizon(trip.id!!, trip.tripPatternId!!, fromStopPathIndex = 1, ctx).filter { it.tripRowId == trip.id }
        assertThat(horizon.size).isGreaterThan(0)
        val expected = expectedSeedArrivals(horizon, ctx)

        val algorithm = KalmanAlgorithm(states, seeds)
        val predictions = algorithm.predict(horizon, serviceDate, adherenceSec = 0, ctx)

        for (stop in horizon) {
            val prediction = predictions.first { it.stopPathIndex == stop.stopPathIndex && it.tripRowId == stop.tripRowId }
            assertThat(prediction.predictedArrivalTs).isEqualTo(expected[stop.stopPathIndex])
            assertThat(prediction.confidenceSec).isNull()
        }
    }

    @Test
    @Order(4)
    fun `KalmanAlgorithm uses learned state over the schedule seed and reports confidence`() {
        val ctx = factory.open(avlFeed)!!
        val trip = trips.findByTripId(ctx.revisionId, "T1")!!
        val horizon = buildHorizon(trip.id!!, trip.tripPatternId!!, fromStopPathIndex = 1, ctx).filter { it.tripRowId == trip.id }
        assertThat(horizon.size).isGreaterThan(0)
        val target = horizon.first()
        val other = horizon.drop(1).firstOrNull()

        states.save(
            KalmanTravelTimeState(
                tripPatternId = target.tripPatternId,
                stopPathIndex = target.stopPathIndex,
                estimateSec = 777.0,
                errorVariance = 64.0,
                sampleCount = 5,
                updatedAt = Instant.now(),
            ),
        )

        val algorithm = KalmanAlgorithm(states, seeds)
        val predictions = algorithm.predict(horizon, serviceDate, adherenceSec = 0, ctx)
        val prediction = predictions.first { it.stopPathIndex == target.stopPathIndex && it.tripRowId == target.tripRowId }

        val anchorSchedule = ctx.scheduleOf(target.tripRowId).first { it.stopPathIndex == target.stopPathIndex }
        val anchorSec = anchorSchedule.arrivalSec ?: anchorSchedule.departureSec!!
        val anchorTs = serviceSecToInstant(serviceDate, anchorSec, ctx.zone)

        assertThat(prediction.predictedArrivalTs).isEqualTo(anchorTs.plusSeconds(777L))
        assertThat(prediction.confidenceSec).isEqualTo(sqrt(64.0).roundToInt())

        if (other != null) {
            val otherPrediction = predictions.first { it.stopPathIndex == other.stopPathIndex && it.tripRowId == other.tripRowId }
            assertThat(otherPrediction.confidenceSec).isNull()
        }
    }
}
