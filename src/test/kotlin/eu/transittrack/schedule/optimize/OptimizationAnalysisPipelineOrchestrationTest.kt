package eu.transittrack.schedule.optimize

import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

import eu.transittrack.avl.model.AvlFeedRepository
import eu.transittrack.gtfs.feed.GtfsFeedRepository
import eu.transittrack.gtfs.model.Trip
import eu.transittrack.gtfs.model.TripRepository
import eu.transittrack.observability.TransitTrackMetrics
import eu.transittrack.predict.model.AvlStopCrossingRepository
import eu.transittrack.predict.model.PredictionAccuracyRepository
import eu.transittrack.schedule.model.ScheduleTimeRepository
import eu.transittrack.schedule.model.StopPathRepository
import eu.transittrack.schedule.optimize.model.OptimizationRecommendationKind
import eu.transittrack.schedule.optimize.model.OptimizationRecommendationRow
import eu.transittrack.schedule.optimize.model.OptimizationRunRow

/** A minimal, directly instantiable [OptimizationAnalyzer] test double — cheaper than mocking
 * `Ordered.getOrder()`'s Kotlin-property mapping through mockito-kotlin. */
private class FakeAnalyzer(
    override val name: String,
    private val order: Int,
    private val behavior: (OptimizationAnalysisContext) -> List<OptimizationRecommendationRow>,
) : OptimizationAnalyzer {
    override fun getOrder(): Int = order

    override fun analyze(context: OptimizationAnalysisContext): List<OptimizationRecommendationRow> = behavior(context)
}

class OptimizationAnalysisPipelineOrchestrationTest {
    private fun eligibleTrip() =
        Trip(
            revisionId = 1, routeId = "R1", serviceId = "WK", tripId = "T1", tripHeadsign = null, tripShortName = null,
            directionId = 0, blockId = null, shapeId = "SHP", wheelchairAccessible = null, bikesAllowed = null,
            tripPatternId = 9, startTimeSec = 0, endTimeSec = 100, frequencyBased = false, noSchedule = false,
        ).apply { id = 1 }

    private fun run() =
        OptimizationRunRow(
            feedId = 1, revisionId = 1, observedFrom = java.time.Instant.EPOCH,
            observedTo = java.time.Instant.EPOCH
                .plusSeconds(1),
            minimumSamples = 1, id = 42,
        )

    private fun pipeline(
        analyzers: List<OptimizationAnalyzer>,
        trips: TripRepository,
        metrics: TransitTrackMetrics = TransitTrackMetrics.forTests(),
    ) = OptimizationAnalysisPipeline(
        gtfsFeeds = mock<GtfsFeedRepository>(),
        avlFeeds = mock<AvlFeedRepository>(),
        trips = trips,
        scheduleTimes = mock<ScheduleTimeRepository>(),
        stopPaths = mock<StopPathRepository>(),
        crossings = mock<AvlStopCrossingRepository>(),
        predictionAccuracy = mock<PredictionAccuracyRepository>(),
        analyzers = analyzers,
        metrics = metrics,
    )

    private fun tripsReturning(trip: Trip): TripRepository {
        val trips = mock<TripRepository>()
        whenever(
            trips.findEligible(1, null, null, null, null, null),
        ).thenReturn(listOf(trip))
        return trips
    }

    private fun row(reason: String) =
        OptimizationRecommendationRow(
            runId = 42, kind = OptimizationRecommendationKind.STOP_TIME, sampleCount = 1, deltaSec = 1, reason = reason,
        )

    @Test
    fun `short-circuits before any analyzer runs when there are no eligible trips`() {
        val trips = mock<TripRepository>()
        whenever(trips.findEligible(1, null, null, null, null, null)).thenReturn(emptyList())
        var called = false
        val analyzer = FakeAnalyzer("noop", 100) {
            called = true
            emptyList()
        }

        val result = pipeline(listOf(analyzer), trips).analyze(run())

        assertThat(result).hasSize(0)
        assertThat(called).isEqualTo(false)
    }

    @Test
    fun `runs analyzers in ascending order regardless of list order`() {
        val trips = tripsReturning(eligibleTrip())
        val callOrder = mutableListOf<String>()
        val second = FakeAnalyzer("second", 200) {
            callOrder += "second"
            emptyList()
        }
        val first = FakeAnalyzer("first", 100) {
            callOrder += "first"
            emptyList()
        }

        pipeline(listOf(second, first), trips).analyze(run())

        assertThat(callOrder).isEqualTo(mutableListOf("first", "second"))
    }

    @Test
    fun `an analyzer that throws does not prevent other analyzers from contributing`() {
        val trips = tripsReturning(eligibleTrip())
        val broken = FakeAnalyzer("broken", 100) { throw RuntimeException("boom") }
        val healthy = FakeAnalyzer("healthy", 200) { listOf(row("ok")) }

        val result = pipeline(listOf(broken, healthy), trips).analyze(run())

        assertThat(result).hasSize(1)
        assertThat(result.single().reason).isEqualTo("ok")
    }

    @Test
    fun `records an analyzer-failure metric when an analyzer throws`() {
        val trips = tripsReturning(eligibleTrip())
        val metrics = mock<TransitTrackMetrics>()
        val broken = FakeAnalyzer("broken", 100) { throw RuntimeException("boom") }

        pipeline(listOf(broken), trips, metrics).analyze(run())

        verify(metrics).optimizationAnalyzerFailure("broken")
    }

    @Test
    fun `a later analyzer observes recommendations recorded by an earlier one`() {
        val trips = tripsReturning(eligibleTrip())
        var seenByLater = -1
        val first = FakeAnalyzer("first", 100) { listOf(row("a"), row("b")) }
        val later = FakeAnalyzer("later", 200) { ctx ->
            seenByLater = ctx.recommendationsSoFar.size
            emptyList()
        }

        pipeline(listOf(first, later), trips).analyze(run())

        assertThat(seenByLater).isEqualTo(2)
    }
}
