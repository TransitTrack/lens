package eu.transittrack.predict.generate

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.isCloseTo
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThan
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

import eu.transittrack.Point
import eu.transittrack.avl.match.MatchContext
import eu.transittrack.avl.match.PatternGeometry
import eu.transittrack.avl.match.SchedulePoint
import eu.transittrack.avl.match.cache.CachedTravelTimesReader
import eu.transittrack.predict.PredictProperties
import eu.transittrack.predict.PredictionAlgorithm
import eu.transittrack.predict.model.PredictionModelRepository
import eu.transittrack.predict.smile.SmileModelStore
import eu.transittrack.predict.smile.StopToStopEstimate
import eu.transittrack.predict.smile.StopToStopFeatures
import eu.transittrack.predict.smile.StopToStopModel
import eu.transittrack.schedule.model.BlockTripRef
import eu.transittrack.schedule.model.StopPathRef
import eu.transittrack.schedule.model.TravelTimesForStopPath
import eu.transittrack.schedule.model.TripRef

/**
 * One pattern with stop paths 0..3 (stops S0..S3); seeds travel 90 s plus 10 s dwell, so the
 * schedule stop-to-stop time is 90 s for stop path 1 and 100 s after it. Trip T1 is scheduled to
 * leave S0 at 08:00. The model is a stub, so every expected time is hand-computable.
 */
class SmileGbmAlgorithmTest {
    private val zone: ZoneId = ZoneOffset.UTC
    private val serviceDate: LocalDate = LocalDate.of(2026, 9, 7)
    private val props = PredictProperties()

    private fun at(sec: Int): Instant = serviceSecToInstant(serviceDate, sec, zone)

    private fun trip(
        id: Long,
        startSec: Int = EIGHT,
        frequencyBased: Boolean = false,
    ) = TripRef(
        id = id, revisionId = REV, tripId = "T$id", routeId = "R1", serviceId = "WK", tripHeadsign = null,
        tripShortName = null, directionId = 0, blockId = null, shapeId = null, wheelchairAccessible = null,
        bikesAllowed = null, tripPatternId = PATTERN, startTimeSec = startSec, endTimeSec = startSec + 300,
        frequencyBased = frequencyBased, noSchedule = false,
    )

    /** Stop path i scheduled to arrive [start] + 100 i and leave on arrival. */
    private fun schedule(start: Int) = (0..3).map { SchedulePoint(it, start + 100 * it, start + 100 * it) }

    private fun seeds(missing: Set<Int> = emptySet()): CachedTravelTimesReader {
        val rows =
            (0..3)
                .filter { it !in missing }
                .associateWith { TravelTimesForStopPath(REV, PATTERN, it, if (it == 0) null else 90, 10) }
        return mock { on { byStopPath(REV, PATTERN) } doReturn rows }
    }

    private fun model(estimate: StopToStopEstimate = StopToStopEstimate(100.0, 80.0, 120.0)): StopToStopModel =
        mock {
            on { routeIndex } doReturn mapOf("R1" to 0)
            on { estimate(any()) } doReturn estimate
        }

    private fun strategy(
        model: StopToStopModel?,
        seeds: CachedTravelTimesReader = seeds(),
    ): SmileGbmAlgorithm {
        val store = SmileModelStore(mock<PredictionModelRepository>(), Clock.systemUTC())
        if (model != null) store.put(FEED, model)
        return SmileGbmAlgorithm(store, seeds, ScheduledLateness(props))
    }

    private fun horizon(
        vararg indices: Int,
        tripRowId: Long = 1,
    ) = indices.map { HorizonStop(tripRowId, PATTERN, it) }

    private fun SmileGbmAlgorithm.run(
        horizon: List<HorizonStop>,
        startTs: Instant,
        ctx: MatchContext,
    ) = predict(horizon, serviceDate, adherenceSec = 0, startTs, ctx, FEED)

    @Test
    fun noModelReturnsEmptyList() {
        val ctx = FakeContext(listOf(trip(1)), mapOf(1L to schedule(EIGHT)))

        assertThat(strategy(model = null).run(horizon(1, 2, 3), at(EIGHT), ctx)).isEmpty()
        // Without the AVL feed there is no model to pick either.
        assertThat(strategy(model()).predict(horizon(1, 2, 3), serviceDate, 0, at(EIGHT), ctx)).isEmpty()
        assertThat(strategy(model()).algorithm).isEqualTo(PredictionAlgorithm.SMILE_GBM)
    }

    @Test
    fun usesModelMedianForStopToStop() {
        val ctx = FakeContext(listOf(trip(1)), mapOf(1L to schedule(EIGHT)))
        val model = model()

        // The vehicle stands at S0 two minutes early: it holds there until its 08:00 departure.
        val predictions = strategy(model).run(horizon(0, 1, 2, 3), at(EIGHT - 120), ctx)

        assertThat(predictions.map { it.stopPathIndex }).isEqualTo(listOf(0, 1, 2, 3))
        assertThat(predictions[0].predictedArrivalTs).isEqualTo(at(EIGHT - 120))
        assertThat(predictions[0].predictedDepartureTs).isEqualTo(at(EIGHT))
        assertThat(predictions.drop(1).map { it.predictedArrivalTs }).isEqualTo(listOf(at(EIGHT + 100), at(EIGHT + 200), at(EIGHT + 300)))
        // The learned arrival-to-arrival time is split into dwell and travel in the seed's proportions.
        assertThat(predictions[1].predictedDepartureTs).isEqualTo(at(EIGHT + 110))

        // Features are built exactly as training builds them, from when the vehicle left S0.
        val features = argumentCaptor<DoubleArray>()
        verify(model, atLeastOnce()).estimate(features.capture())
        val expected =
            StopToStopFeatures.featuresFor(serviceDate, zone, at(EIGHT), 1, 90.0, EIGHT, 0, "R1", mapOf("R1" to 0))
        assertThat(features.firstValue.toList()).isEqualTo(expected.toList())
    }

    @Test
    fun rangeIsSymmetricAndMatchesSpread() {
        val ctx = FakeContext(listOf(trip(1)), mapOf(1L to schedule(EIGHT)))

        val predictions = strategy(model(StopToStopEstimate(100.0, 80.0, 120.0))).run(horizon(1, 2), at(EIGHT), ctx)

        val second = predictions.single { it.stopPathIndex == 2 }
        val confidence = second.confidenceSec!!
        assertThat(confidence.toDouble()).isCloseTo(sqrt(2.0) * 15.6, 1.0)
        assertThat(second.rangeHighSec).isEqualTo((1.2816 * confidence).roundToInt())
        assertThat(second.rangeLowSec).isEqualTo(-second.rangeHighSec!!)
        val first = predictions.single { it.stopPathIndex == 1 }
        assertThat(first.rangeHighSec).isEqualTo((1.2816 * first.confidenceSec!!).roundToInt())
        assertThat(first.rangeLowSec).isEqualTo(-first.rangeHighSec!!)
    }

    @Test
    fun firstStopWithoutConfidenceGetsNoRangeOrLateness() {
        val ctx = FakeContext(listOf(trip(1)), mapOf(1L to schedule(EIGHT)))

        val first = strategy(model()).run(horizon(0, 1), at(EIGHT - 120), ctx).first()

        assertThat(first.confidenceSec).isNull()
        assertThat(first.rangeLowSec).isNull()
        assertThat(first.rangeHighSec).isNull()
        assertThat(first.late).isNull()
        assertThat(first.lateProbability).isNull()
    }

    @Test
    fun continuationTripFirstStopGetsNoRangeOrLateness() {
        // T1 runs to S3, then the block continues as T2 from S0 at 08:10. T2's stop path 0 is a
        // departure event: it keeps the accumulated confidence but gets no arrival-anchored range or late flag.
        val ctx =
            FakeContext(
                listOf(trip(1), trip(2, EIGHT + 600)),
                mapOf(1L to schedule(EIGHT), 2L to schedule(EIGHT + 600)),
            )

        val predictions = strategy(model()).run(horizon(1, 2, 3) + horizon(0, 1, 2, tripRowId = 2), at(EIGHT), ctx)

        val continuation = predictions.single { it.tripRowId == 2L && it.stopPathIndex == 0 }
        assertThat(continuation.confidenceSec).isNotNull()
        assertThat(continuation.rangeLowSec).isNull()
        assertThat(continuation.rangeHighSec).isNull()
        assertThat(continuation.lateProbability).isNull()
        assertThat(continuation.late).isNull()
        for (p in predictions.filter { it.stopPathIndex > 0 }) {
            assertThat(p.rangeHighSec).isNotNull()
            assertThat(p.late).isNotNull()
        }
        assertThat(predictions.count { it.tripRowId == 2L && it.stopPathIndex > 0 }).isEqualTo(2)
    }

    @Test
    fun lateWhenPredictedBeyondTolerance() {
        // Predicted at S1 at 08:01:40; scheduled 10 minutes earlier.
        val ctx = FakeContext(listOf(trip(1)), mapOf(1L to schedule(EIGHT + 100 - 600 - 100)))

        val p = strategy(model()).run(horizon(1), at(EIGHT), ctx).single()

        assertThat(p.predictedArrivalTs).isEqualTo(at(EIGHT + 100))
        assertThat(p.late).isEqualTo(true)
        assertThat(p.lateProbability!!).isGreaterThan(0.99)
    }

    @Test
    fun scheduledDepartureStandsInForMissingArrival() {
        // S1 has only a scheduled departure, 10 minutes before the predicted 08:01:40 arrival.
        val ctx =
            FakeContext(
                listOf(trip(1)),
                mapOf(1L to schedule(EIGHT).map { if (it.stopPathIndex == 1) SchedulePoint(1, null, EIGHT + 100 - 600) else it }),
            )

        val p = strategy(model()).run(horizon(1), at(EIGHT), ctx).single()

        assertThat(p.late).isEqualTo(true)
        assertThat(p.lateProbability).isNotNull()
    }

    @Test
    fun onTimeWithinTolerance() {
        // Predicted three minutes behind schedule: inside the 300 s tolerance.
        val ctx = FakeContext(listOf(trip(1)), mapOf(1L to schedule(EIGHT - 180)))

        val p = strategy(model()).run(horizon(1), at(EIGHT), ctx).single()

        assertThat(p.late).isEqualTo(false)
        assertThat(p.lateProbability).isNotNull()
    }

    @Test
    fun frequentRouteUsesScheduledHeadway() {
        // Same three minutes behind as above, but trips 2 and 3 follow every 4 minutes: the margin is
        // half the scheduled headway (120 s), so the vehicle is late.
        val ctx =
            FakeContext(
                listOf(trip(1), trip(2, EIGHT - 180 + 240), trip(3, EIGHT - 180 + 480)),
                mapOf(1L to schedule(EIGHT - 180), 2L to schedule(EIGHT - 180 + 240), 3L to schedule(EIGHT - 180 + 480)),
            )

        val p = strategy(model()).run(horizon(1), at(EIGHT), ctx).single()

        assertThat(p.late).isEqualTo(true)
    }

    @Test
    fun frequencyBasedTripGetsRange() {
        // A frequency-based trip's stop times are offsets from whenever a run starts: no clock-time
        // departure to hold for, and no scheduled arrival to be late against.
        val ctx = FakeContext(listOf(trip(1, startSec = 0, frequencyBased = true)), mapOf(1L to schedule(0)))

        val predictions = strategy(model()).run(horizon(0, 1, 2), at(EIGHT), ctx)

        assertThat(predictions.map { it.stopPathIndex }).isEqualTo(listOf(0, 1, 2))
        // It leaves S0 once its dwell there is done, not at a scheduled departure.
        assertThat(predictions[0].predictedDepartureTs).isEqualTo(at(EIGHT + 10))
        assertThat(predictions[1].predictedArrivalTs).isEqualTo(at(EIGHT + 110))
        for (p in predictions.drop(1)) {
            assertThat(p.rangeHighSec).isNotNull()
            assertThat(p.rangeLowSec).isEqualTo(-p.rangeHighSec!!)
            assertThat(p.late).isNull()
        }
    }

    @Test
    fun missingSeedStopsTheWalkWithoutNaN() {
        val ctx = FakeContext(listOf(trip(1)), mapOf(1L to schedule(EIGHT)))

        val predictions = strategy(model(), seeds(missing = setOf(2))).run(horizon(1, 2, 3), at(EIGHT), ctx)

        assertThat(predictions.map { it.stopPathIndex }).isEqualTo(listOf(1))
        assertValid(predictions, at(EIGHT))
    }

    @Test
    fun nonFiniteEstimateStopsTheWalk() {
        val ctx = FakeContext(listOf(trip(1)), mapOf(1L to schedule(EIGHT)))
        val model = model()
        whenever(model.estimate(argThat { this[0] == 2.0 })) doReturn StopToStopEstimate(Double.NaN, 80.0, 120.0)

        val predictions = strategy(model).run(horizon(1, 2, 3), at(EIGHT), ctx)

        assertThat(predictions.map { it.stopPathIndex }).isEqualTo(listOf(1))
        assertValid(predictions, at(EIGHT))
    }

    @Test
    fun infiniteSpreadStopsTheWalk() {
        val ctx = FakeContext(listOf(trip(1)), mapOf(1L to schedule(EIGHT)))
        val model = model()
        whenever(model.estimate(argThat { this[0] == 2.0 })) doReturn StopToStopEstimate(100.0, 0.0, Double.POSITIVE_INFINITY)

        val predictions = strategy(model).run(horizon(1, 2, 3), at(EIGHT), ctx)

        assertThat(predictions.map { it.stopPathIndex }).isEqualTo(listOf(1))
    }

    @Test
    fun estimateFailureStopsTheWalkWithoutThrowing() {
        val ctx = FakeContext(listOf(trip(1)), mapOf(1L to schedule(EIGHT)))
        val model = model()
        whenever(model.estimate(argThat { this[0] == 2.0 })) doThrow IllegalArgumentException("expected 7 features, got 6")

        val predictions = strategy(model).run(horizon(1, 2, 3), at(EIGHT), ctx)

        assertThat(predictions.map { it.stopPathIndex }).isEqualTo(listOf(1))
    }

    private fun assertValid(
        predictions: List<GeneratedPrediction>,
        startTs: Instant,
    ) {
        for (p in predictions) {
            assertThat(p.predictedArrivalTs!!.isBefore(startTs)).isFalse()
            assertThat(p.predictedDepartureTs!!.isBefore(p.predictedArrivalTs!!)).isFalse()
            assertThat(p.confidenceSec!! >= 0).isTrue()
            assertThat(p.lateProbability?.isFinite() ?: true).isTrue()
        }
    }

    /** A context over [trips] of one pattern (stops S0..S3), every service active. */
    private class FakeContext(
        private val trips: List<TripRef>,
        private val schedules: Map<Long, List<SchedulePoint>>,
    ) : MatchContext {
        override val revisionId = REV
        override val zone: ZoneId = ZoneOffset.UTC

        override fun patternGeometry(tripPatternId: Long): PatternGeometry? = null

        override fun scheduleOf(tripRowId: Long): List<SchedulePoint> = schedules[tripRowId].orEmpty()

        override fun trip(tripRowId: Long): TripRef? = trips.firstOrNull { it.id == tripRowId }

        override fun tripByGtfsId(tripId: String): TripRef? = trips.firstOrNull { it.tripId == tripId }

        override fun blockTripOf(tripRowId: Long): BlockTripRef? = null

        override fun nextBlockTrip(
            blockPk: Long,
            listIndex: Int,
        ): BlockTripRef? = null

        override fun activeServiceIds(date: LocalDate): Set<String> = setOf("WK")

        override fun candidateTrips(serviceIds: Set<String>): List<TripRef> = trips.filter { it.serviceId in serviceIds }

        override fun candidateTripsForRoute(
            routeId: String,
            serviceIds: Set<String>,
        ): List<TripRef> = candidateTrips(serviceIds).filter { it.routeId == routeId }

        override fun patternStops(tripPatternId: Long): List<StopPathRef> =
            (0..3).map {
                StopPathRef(
                    id = it.toLong(), revisionId = REV, tripPatternId = tripPatternId, stopPathIndex = it, stopId = "S$it",
                    routeId = "R1", stopSeq = it, lengthM = 500.0, pathGeometry = null, pickupType = null, dropOffType = null,
                    waitStop = false, scheduleAdherenceStop = false, layoverStop = false, breakTimeSec = null,
                )
            }

        override fun patternExtentWithin(
            tripPatternId: Long,
            p: Point,
            distanceM: Double,
        ): Boolean = false
    }

    private companion object {
        const val REV = 1L
        const val PATTERN = 10L
        const val FEED = "avl-1"
        const val EIGHT = 8 * 3600
    }
}
