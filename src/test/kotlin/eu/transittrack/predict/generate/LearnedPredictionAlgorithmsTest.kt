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
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary

import eu.transittrack.AvlAssignmentMode
import eu.transittrack.AvlFormat
import eu.transittrack.TestcontainersConfiguration
import eu.transittrack.avl.match.AvlMatchContext
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

/**
 * [schedule-sample]'s T1 has stop paths 0..3 (08:00/08:10/08:20/08:30). Stop path 0's
 * `travel_times_for_stop_path.travelTimeSec` is always `null` (there is no "previous" stop to
 * measure travel from — see `ScheduleInterpolator.resolve`), so every horizon in this test starts
 * at `fromStopPathIndex = 1` to stay clear of that intentional gap; the horizon is then `[1, 2,
 * 3]`.
 *
 * Each test targets a different stop-path index for its "this index carries a learned value"
 * assertion (2 for `TravelTimeObservation`, 3 for `KalmanTravelTimeState`) so that no test's
 * fallback assertion depends on another test *not yet* having seeded a row — the two learned-value
 * tables are independent of each other, and within a table each test either writes to an index no
 * other test asserts a fallback on, or only asserts fallback behavior for an index no test ever
 * writes to. This makes the whole class independent of method execution order.
 */
@SpringBootTest(classes = [eu.transittrack.Application::class])
@Import(TestcontainersConfiguration::class, LearnedPredictionAlgorithmsTest.Stub::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
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

    /** A fixed, easy-to-hand-verify stand-in for "the vehicle's current position timestamp". */
    private fun fixedStartTs(ctx: AvlMatchContext): Instant = serviceDate.atStartOfDay(ctx.zone).toInstant()

    private fun t1Horizon(ctx: AvlMatchContext): List<HorizonStop> {
        val trip = trips.findByTripId(ctx.revisionId, "T1")!!
        return buildHorizon(trip.id!!, trip.tripPatternId!!, fromStopPathIndex = 1, ctx).filter { it.tripRowId == trip.id }
    }

    /**
     * Recomputes, straight from the seed repository, the same accumulation the schedule-seed
     * fallback performs: starting from [startTs], walk forward adding each stop's seeded travel +
     * dwell time. Used to independently verify the schedule-seed fallback path.
     */
    private fun expectedSeedArrivals(
        horizon: List<HorizonStop>,
        ctx: AvlMatchContext,
        startTs: Instant,
    ): Map<Int, Instant> {
        var lastTs = startTs
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
    fun `HistoricalAverageAlgorithm falls back to schedule seed with no observations`() {
        val ctx = factory.open(avlFeed)!!
        val horizon = t1Horizon(ctx)
        assertThat(horizon.size).isGreaterThan(0)
        val startTs = fixedStartTs(ctx)
        val expected = expectedSeedArrivals(horizon, ctx, startTs)

        val algorithm = HistoricalAverageAlgorithm(observations, seeds)
        val predictions = algorithm.predict(horizon, serviceDate, adherenceSec = 0, startTs, ctx)

        // Stop path 1 is never written to by any other test in this class.
        val prediction = predictions.first { it.stopPathIndex == 1 }
        assertThat(prediction.predictedArrivalTs).isEqualTo(expected[1])
    }

    @Test
    fun `HistoricalAverageAlgorithm uses learned observation over the schedule seed`() {
        val ctx = factory.open(avlFeed)!!
        val horizon = t1Horizon(ctx)
        assertThat(horizon.size).isGreaterThan(0)
        val startTs = fixedStartTs(ctx)
        val target = horizon.first { it.stopPathIndex == 2 }

        observations.save(
            TravelTimeObservation(
                tripPatternId = target.tripPatternId,
                stopPathIndex = target.stopPathIndex,
                sampleCount = 5,
                meanSec = 999.0,
                updatedAt = Instant.now(),
            ),
        )

        // Everything strictly before stop path 2 still comes from the schedule seed, so the
        // instant just before stop path 2's travel time is applied is independently verifiable.
        val beforeTarget = horizon.takeWhile { it.stopPathIndex < target.stopPathIndex }
        val expectedBefore = expectedSeedArrivals(beforeTarget, ctx, startTs)
        val tsBeforeTarget = if (beforeTarget.isEmpty()) startTs else expectedBefore.getValue(beforeTarget.last().stopPathIndex)

        val algorithm = HistoricalAverageAlgorithm(observations, seeds)
        val predictions = algorithm.predict(horizon, serviceDate, adherenceSec = 0, startTs, ctx)
        val prediction = predictions.first { it.stopPathIndex == 2 }

        assertThat(prediction.predictedArrivalTs).isEqualTo(tsBeforeTarget.plusSeconds(999L))
    }

    @Test
    fun `KalmanAlgorithm falls back to schedule seed with no state and reports null confidence`() {
        val ctx = factory.open(avlFeed)!!
        val horizon = t1Horizon(ctx)
        assertThat(horizon.size).isGreaterThan(0)
        val startTs = fixedStartTs(ctx)
        val expected = expectedSeedArrivals(horizon, ctx, startTs)

        val algorithm = KalmanAlgorithm(states, seeds)
        val predictions = algorithm.predict(horizon, serviceDate, adherenceSec = 0, startTs, ctx)

        // Stop paths 1 and 2 are never written to by any Kalman-state-seeding test in this class.
        for (idx in listOf(1, 2)) {
            val prediction = predictions.first { it.stopPathIndex == idx }
            assertThat(prediction.predictedArrivalTs).isEqualTo(expected[idx])
            assertThat(prediction.confidenceSec).isNull()
        }
    }

    @Test
    fun `KalmanAlgorithm uses learned state over the schedule seed and reports confidence`() {
        val ctx = factory.open(avlFeed)!!
        val horizon = t1Horizon(ctx)
        assertThat(horizon.size).isGreaterThan(0)
        val startTs = fixedStartTs(ctx)
        val target = horizon.first { it.stopPathIndex == 3 }

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

        // Everything strictly before stop path 3 still comes from the schedule seed.
        val beforeTarget = horizon.takeWhile { it.stopPathIndex < target.stopPathIndex }
        val expectedBefore = expectedSeedArrivals(beforeTarget, ctx, startTs)
        val tsBeforeTarget = if (beforeTarget.isEmpty()) startTs else expectedBefore.getValue(beforeTarget.last().stopPathIndex)

        val algorithm = KalmanAlgorithm(states, seeds)
        val predictions = algorithm.predict(horizon, serviceDate, adherenceSec = 0, startTs, ctx)
        val prediction = predictions.first { it.stopPathIndex == 3 }

        assertThat(prediction.predictedArrivalTs).isEqualTo(tsBeforeTarget.plusSeconds(777L))
        assertThat(prediction.confidenceSec).isEqualTo(sqrt(64.0).roundToInt())
    }
}
