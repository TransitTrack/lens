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
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary

import eu.transittrack.AvlAssignmentMode
import eu.transittrack.AvlFormat
import eu.transittrack.avl.match.AvlMatchContext
import eu.transittrack.avl.match.AvlMatchContextFactory
import eu.transittrack.avl.match.cache.CachedLearningKeyReader
import eu.transittrack.avl.match.cache.CachedTravelTimesReader
import eu.transittrack.avl.model.AvlFeed
import eu.transittrack.avl.model.AvlFeedSourceKind
import eu.transittrack.gtfs.download.FeedDownloader
import eu.transittrack.gtfs.feed.FeedInput
import eu.transittrack.gtfs.feed.GtfsFeedService
import eu.transittrack.gtfs.ingest.IngestionService
import eu.transittrack.gtfs.model.TripRepository
import eu.transittrack.gtfs.support.FixtureDownloader
import eu.transittrack.predict.PredictProperties
import eu.transittrack.predict.learn.DayType
import eu.transittrack.predict.learn.TimeBand
import eu.transittrack.predict.learn.TravelTimeBucket
import eu.transittrack.predict.model.KalmanTravelTimeState
import eu.transittrack.predict.model.KalmanTravelTimeStateRepository
import eu.transittrack.predict.model.TravelTimeObservation
import eu.transittrack.predict.model.TravelTimeObservationRepository
import eu.transittrack.schedule.model.TravelTimesForStopPath
import eu.transittrack.schedule.model.TravelTimesForStopPathRepository
import eu.transittrack.support.PostgresPerClassTest

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
@Import(LearnedPredictionAlgorithmsTest.Stub::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LearnedPredictionAlgorithmsTest(
    @Autowired val factory: AvlMatchContextFactory,
    @Autowired val feedService: GtfsFeedService,
    @Autowired val ingestion: IngestionService,
    @Autowired val trips: TripRepository,
    @Autowired val observations: TravelTimeObservationRepository,
    @Autowired val states: KalmanTravelTimeStateRepository,
    @Autowired val seeds: TravelTimesForStopPathRepository,
    @Autowired val learningKeys: CachedLearningKeyReader,
    @Autowired val seedReader: CachedTravelTimesReader,
    @Autowired val props: PredictProperties,
) : PostgresPerClassTest() {
    @TestConfiguration(proxyBeanMethods = false)
    class Stub {
        @Bean
        @Primary
        fun dl(): FeedDownloader = FixtureDownloader("schedule-sample")
    }

    @BeforeAll
    fun ingest() {
        truncateBeforeFixture()
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

    private fun historical() = HistoricalAverageAlgorithm(observations, seedReader, learningKeys, props)

    private fun kalman() = KalmanAlgorithm(states, seedReader, learningKeys, props)

    private fun keyOf(
        ctx: AvlMatchContext,
        stop: HorizonStop,
    ) = learningKeys.keyOf(ctx.revisionId, stop.tripPatternId)!!

    /** A fixed, easy-to-hand-verify stand-in for "the vehicle's current position timestamp". */
    private fun fixedStartTs(ctx: AvlMatchContext): Instant = serviceDate.atStartOfDay(ctx.zone).toInstant()

    /** T1's stops 1..3, the vehicle just leaving stop 0 (whole stop path 1 still ahead). */
    private fun t1Horizon(ctx: AvlMatchContext): List<HorizonStop> {
        val trip = trips.findByTripId(ctx.revisionId, "T1")!!
        return (1..3).map { HorizonStop(trip.id!!, trip.tripPatternId!!, it) }
    }

    /**
     * Recomputes, straight from the seed repository, the same accumulation the schedule-seed
     * fallback performs: starting from [startTs], walk forward adding each stop's seeded travel +
     * dwell time. Used to independently verify the schedule-seed fallback path. (Equivalent to
     * summing seed stop-to-stop times - travel plus the previous stop's dwell - arrival to arrival.)
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

        val algorithm = historical()
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

        val key = keyOf(ctx, target)
        observations.save(
            TravelTimeObservation(
                gtfsFeedCode = key.gtfsFeedCode,
                patternKey = key.patternKey,
                stopPathIndex = target.stopPathIndex,
                dayType = DayType.ALL,
                timeBand = TimeBand.ALL,
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

        val algorithm = historical()
        val predictions = algorithm.predict(horizon, serviceDate, adherenceSec = 0, startTs, ctx)
        val prediction = predictions.first { it.stopPathIndex == 2 }

        assertThat(prediction.predictedArrivalTs).isEqualTo(tsBeforeTarget.plusSeconds(999L))
    }

    @Test
    fun `KalmanAlgorithm includes conservative uncertainty for schedule fallback`() {
        val ctx = factory.open(avlFeed)!!
        val horizon = t1Horizon(ctx)
        assertThat(horizon.size).isGreaterThan(0)
        val startTs = fixedStartTs(ctx)
        val expected = expectedSeedArrivals(horizon, ctx, startTs)

        val algorithm = kalman()
        val predictions = algorithm.predict(horizon, serviceDate, adherenceSec = 0, startTs, ctx)

        // Stop paths 1 and 2 are never written to by any Kalman-state-seeding test in this class.
        for (idx in listOf(1, 2)) {
            val prediction = predictions.first { it.stopPathIndex == idx }
            assertThat(prediction.predictedArrivalTs).isEqualTo(expected[idx])
            assertThat(prediction.confidenceSec).isEqualTo(
                sqrt(idx * (props.learn.kalmanInitialVarianceSec2 + props.learn.kalmanMeasurementNoiseSec2)).roundToInt(),
            )
        }
    }

    @Test
    fun `KalmanAlgorithm uses learned state over the schedule seed and reports confidence`() {
        val ctx = factory.open(avlFeed)!!
        val horizon = t1Horizon(ctx)
        assertThat(horizon.size).isGreaterThan(0)
        val startTs = fixedStartTs(ctx)
        val target = horizon.first { it.stopPathIndex == 3 }

        val key = keyOf(ctx, target)
        states.save(
            KalmanTravelTimeState(
                gtfsFeedCode = key.gtfsFeedCode,
                patternKey = key.patternKey,
                stopPathIndex = target.stopPathIndex,
                dayType = DayType.ALL,
                timeBand = TimeBand.ALL,
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

        val algorithm = kalman()
        val predictions = algorithm.predict(horizon, serviceDate, adherenceSec = 0, startTs, ctx)
        val prediction = predictions.first { it.stopPathIndex == 3 }

        assertThat(prediction.predictedArrivalTs).isEqualTo(tsBeforeTarget.plusSeconds(777L))
        // The estimate's own variance plus the trip-to-trip spread of one vehicle around it.
        assertThat(prediction.confidenceSec).isEqualTo(
            sqrt(
                2 *
                    (props.learn.kalmanInitialVarianceSec2 + props.learn.kalmanMeasurementNoiseSec2) +
                    64.0 +
                    props.learn.kalmanMeasurementNoiseSec2,
            ).roundToInt(),
        )
    }

    private fun seedTravelSec(
        ctx: AvlMatchContext,
        tripPatternId: Long,
        stopPathIndex: Int,
    ): Int = seeds.findByTripPatternOrdered(ctx.revisionId, tripPatternId).first { it.stopPathIndex == stopPathIndex }.travelTimeSec!!

    @Test
    fun `only the uncovered share of the vehicle's current stop path is counted`() {
        val ctx = factory.open(avlFeed)!!
        val trip = trips.findByTripId(ctx.revisionId, "T1")!!
        val startTs = fixedStartTs(ctx)
        val travel1 = seedTravelSec(ctx, trip.tripPatternId!!, 1)
        // Three quarters of stop path 1 already behind the vehicle. Stop path 1 carries no
        // learned observation in this class, so this is the schedule seed.
        val horizon = listOf(HorizonStop(trip.id!!, trip.tripPatternId!!, 1, remainingFraction = 0.25))

        val prediction = historical().predict(horizon, serviceDate, null, startTs, ctx).single()

        assertThat(prediction.predictedArrivalTs).isEqualTo(startTs.plusMillis((travel1 * 0.25 * 1000).toLong()))
    }

    @Test
    fun `a vehicle standing at its first stop is held until the scheduled departure`() {
        val ctx = factory.open(avlFeed)!!
        val trip = trips.findByTripId(ctx.revisionId, "T1")!!
        val scheduledDeparture =
            serviceSecToInstant(serviceDate, ctx.scheduleOf(trip.id!!).first { it.stopPathIndex == 0 }.departureSec!!, ctx.zone)
        val travel1 = seedTravelSec(ctx, trip.tripPatternId!!, 1)
        val horizon = (0..1).map { HorizonStop(trip.id!!, trip.tripPatternId!!, it) }

        // Waiting at the terminus ten minutes early: it doesn't leave before its departure time.
        val predictions =
            historical().predict(horizon, serviceDate, null, scheduledDeparture.minusSeconds(600), ctx)

        assertThat(predictions.first { it.stopPathIndex == 0 }.predictedDepartureTs).isEqualTo(scheduledDeparture)
        assertThat(predictions.first { it.stopPathIndex == 1 }.predictedArrivalTs)
            .isEqualTo(scheduledDeparture.plusSeconds(travel1.toLong()))
    }

    @Test
    fun `a well-sampled time band wins over the overall value, for the band the stop path is driven in`() {
        val ctx = factory.open(avlFeed)!!
        val horizon = t1Horizon(ctx)
        val startTs = fixedStartTs(ctx) // Monday just after midnight: stop path 3 is driven at night
        val target = horizon.first { it.stopPathIndex == 3 }
        val key = keyOf(ctx, target)

        fun observation(
            bucket: TravelTimeBucket,
            meanSec: Double,
            samples: Long,
        ) = TravelTimeObservation(
            gtfsFeedCode = key.gtfsFeedCode, patternKey = key.patternKey, stopPathIndex = 3,
            dayType = bucket.dayType, timeBand = bucket.timeBand, sampleCount = samples, meanSec = meanSec, updatedAt = Instant.now(),
        )
        // Stop path 3 carries no other historical observation in this class.
        observations.saveAll(
            listOf(
                observation(TravelTimeBucket.OVERALL, 999.0, samples = 500),
                observation(TravelTimeBucket(DayType.WEEKDAY, TimeBand.NIGHT), 555.0, samples = props.learn.minBucketSamples.toLong()),
                observation(TravelTimeBucket(DayType.WEEKDAY, TimeBand.MORNING_PEAK), 111.0, samples = 500),
            ),
        )
        val predictions = historical().predict(horizon, serviceDate, null, startTs, ctx)
        val arrival2 = predictions.first { it.stopPathIndex == 2 }.predictedArrivalTs!!

        assertThat(predictions.first { it.stopPathIndex == 3 }.predictedArrivalTs).isEqualTo(arrival2.plusSeconds(555L))
    }

    @Test
    fun `learned dwell and travel share one consistent arrival-to-arrival duration`() {
        val ctx = factory.open(avlFeed)!!
        val horizon = t1Horizon(ctx)
        val patternId = horizon.first().tripPatternId
        val customSeeds = mock<CachedTravelTimesReader>()
        whenever(customSeeds.byStopPath(ctx.revisionId, patternId)).thenReturn(
            (0..3).associateWith { index ->
                TravelTimesForStopPath(ctx.revisionId, patternId, index, if (index == 0) null else 60, if (index in 1..2) 180 else 0)
            },
        )
        val learned = (1..3).associateWith { mapOf(TravelTimeBucket.OVERALL to LearnedStopToStop(60.0, null, 100)) }
        val start = fixedStartTs(ctx)
        val predictions = walkHorizon(horizon, serviceDate, start, ctx, customSeeds, 10) { learned }
        assertThat(predictions.map { it.predictedArrivalTs }).isEqualTo(listOf(60L, 120L, 180L).map { start.plusSeconds(it) })
        assertThat(predictions.first().predictedDepartureTs).isEqualTo(start.plusSeconds(105))
        assertThat(predictions.zipWithNext().all { (a, b) -> !b.predictedArrivalTs!!.isBefore(a.predictedDepartureTs!!) }).isEqualTo(true)

        val onPath2 = walkHorizon(listOf(horizon[1].copy(remainingFraction = 0.5)), serviceDate, start, ctx, customSeeds, 10) { learned }
        assertThat(onPath2.single().predictedArrivalTs).isEqualTo(start.plusMillis(7500))
    }

    @Test
    fun `unlearned legs add uncertainty instead of leaving partially learned journeys overconfident`() {
        val ctx = factory.open(avlFeed)!!
        val horizon = t1Horizon(ctx)
        val learned = mapOf(
            1 to mapOf(TravelTimeBucket.OVERALL to LearnedStopToStop(60.0, 4.0, 100)),
            3 to mapOf(TravelTimeBucket.OVERALL to LearnedStopToStop(60.0, 9.0, 100)),
        )
        val predictions =
            walkHorizon(horizon, serviceDate, fixedStartTs(ctx), ctx, seedReader, 10, fallbackVarianceSec2 = 4000.0) { learned }
        assertThat(predictions.last().confidenceSec).isEqualTo(sqrt(4.0 + 4000.0 + 9.0).roundToInt())
    }

    @Test
    fun `old Kalman state has wider uncertainty without silently changing its mean`() {
        val ctx = factory.open(avlFeed)!!
        val horizon = t1Horizon(ctx).take(1)
        val key = keyOf(ctx, horizon.first())
        val start = fixedStartTs(ctx)
        val state = KalmanTravelTimeState(
            gtfsFeedCode = key.gtfsFeedCode, patternKey = key.patternKey, stopPathIndex = 1,
            dayType = DayType.ALL, timeBand = TimeBand.ALL, estimateSec = 600.0, errorVariance = 64.0, sampleCount = 100, updatedAt = start,
        )
        val repository = mock<KalmanTravelTimeStateRepository>()
        whenever(repository.findByPattern(key.gtfsFeedCode, key.patternKey)).thenReturn(listOf(state))
        val algorithm = KalmanAlgorithm(repository, seedReader, learningKeys, props)
        val fresh = algorithm.predict(horizon, serviceDate, null, start, ctx).single()
        state.updatedAt = start.minusSeconds(6 * 3600)
        val aged = algorithm.predict(horizon, serviceDate, null, start, ctx).single()
        assertThat(aged.predictedArrivalTs).isEqualTo(fresh.predictedArrivalTs)
        val expectedVariance = 64.0 + props.learn.kalmanMeasurementNoiseSec2 + 6 * props.learn.kalmanVarianceGrowthSec2PerHour
        assertThat(aged.confidenceSec).isEqualTo(sqrt(expectedVariance).roundToInt())
        assertThat(aged.confidenceSec!!).isGreaterThan(fresh.confidenceSec!!)
    }
}
