package eu.transittrack.schedule.optimize

import java.time.Instant
import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import org.springframework.beans.factory.annotation.Autowired

import eu.transittrack.gtfs.feed.FeedSource
import eu.transittrack.gtfs.feed.GtfsFeed
import eu.transittrack.gtfs.feed.GtfsFeedRepository
import eu.transittrack.gtfs.model.Trip
import eu.transittrack.gtfs.model.TripRepository
import eu.transittrack.gtfs.revision.GtfsRevision
import eu.transittrack.gtfs.revision.GtfsRevisionRepository
import eu.transittrack.gtfs.revision.GtfsRevisionStatus
import eu.transittrack.gtfs.support.PostgresSliceTest
import eu.transittrack.predict.model.AvlStopCrossingRepository
import eu.transittrack.predict.model.PredictionAccuracyRepository
import eu.transittrack.schedule.model.ScheduleTime
import eu.transittrack.schedule.model.ScheduleTimeRepository
import eu.transittrack.schedule.model.StopPath
import eu.transittrack.schedule.model.StopPathRepository
import eu.transittrack.schedule.model.TripPattern
import eu.transittrack.schedule.model.TripPatternRepository
import eu.transittrack.schedule.optimize.model.OptimizationRecommendationKind
import eu.transittrack.schedule.optimize.model.OptimizationRecommendationRow
import eu.transittrack.schedule.optimize.model.OptimizationRunRepository
import eu.transittrack.schedule.optimize.model.OptimizationRunRow
import eu.transittrack.support.PostgresPerMethodTest

@PostgresSliceTest
class OptimizationAnalysisContextTest(
    @Autowired val feeds: GtfsFeedRepository,
    @Autowired val revisions: GtfsRevisionRepository,
    @Autowired val patterns: TripPatternRepository,
    @Autowired val stopPaths: StopPathRepository,
    @Autowired val trips: TripRepository,
    @Autowired val scheduleTimes: ScheduleTimeRepository,
    @Autowired val crossings: AvlStopCrossingRepository,
    @Autowired val accuracies: PredictionAccuracyRepository,
    @Autowired val runs: OptimizationRunRepository,
) : PostgresPerMethodTest() {
    private fun seedRun(): Pair<OptimizationRunRow, Trip> {
        val feed = feeds.save(GtfsFeed("g", "G", null, "http://x/z.zip", null, true, null, FeedSource.API, Instant.now(), Instant.now()))
        val revision = revisions.save(GtfsRevision(feedId = feed.id!!, status = GtfsRevisionStatus.READY, sourceUrl = "http://x/z.zip"))
        val pattern =
            patterns.save(
                TripPattern(
                    revisionId = revision.id!!,
                    patternKey = "SHP|S1_to_S2|R1",
                    routeId = "R1",
                    routeShortName = null,
                    directionId = 0,
                    headsign = "To S2",
                    shapeId = "SHP",
                    stopCount = 2,
                    lengthM = 1000.0,
                    tripCount = 1,
                ),
            )
        stopPaths.save(
            StopPath(
                revision.id!!, pattern.id!!, 0, "S1", "R1", 1, 0.0, null, null, null,
                waitStop = true, scheduleAdherenceStop = true, layoverStop = true, breakTimeSec = null,
            ),
        )
        val trip =
            trips.save(
                Trip(
                    revisionId = revision.id!!, routeId = "R1", serviceId = "WK", tripId = "T1", tripHeadsign = null,
                    tripShortName = null, directionId = 0, blockId = null, shapeId = "SHP", wheelchairAccessible = null,
                    bikesAllowed = null, tripPatternId = pattern.id!!, startTimeSec = 0, endTimeSec = 1000,
                    frequencyBased = false, noSchedule = false,
                ),
            )
        scheduleTimes
            .save(ScheduleTime(revision.id!!, trip.id!!, 0, 0, 0, interpolated = false, schedTravelTimeSec = null, schedDwellTimeSec = 0))
        val run =
            runs.save(
                OptimizationRunRow(
                    feedId = feed.id!!, revisionId = revision.id!!, observedFrom = Instant.parse("2026-09-01T00:00:00Z"),
                    observedTo = Instant.parse("2026-09-08T00:00:00Z"), minimumSamples = 1,
                ),
            )
        return run to trip
    }

    private fun context(
        run: OptimizationRunRow,
        eligibleTrips: List<Trip>,
        avlFeedIdsCalls: MutableList<Unit> = mutableListOf(),
    ) = OptimizationAnalysisContext(
        run = run,
        eligibleTrips = eligibleTrips,
        avlFeedIdsSupplier = {
            avlFeedIdsCalls += Unit
            emptyList()
        },
        crossingsSupplier = { emptyList() },
        scheduleTimes = scheduleTimes,
        stopPaths = stopPaths,
        predictionAccuracy = accuracies,
    )

    @Test
    fun `does not invoke the AVL feed id supplier until first access`() {
        val (run, trip) = seedRun()
        val calls = mutableListOf<Unit>()
        val ctx = context(run, listOf(trip), calls)

        assertThat(calls).hasSize(0)
        ctx.avlFeedIds
        assertThat(calls).hasSize(1)
        ctx.avlFeedIds
        assertThat(calls).hasSize(1)
    }

    @Test
    fun `memoizes scheduleFor per trip row id`() {
        val (run, trip) = seedRun()
        val ctx = context(run, listOf(trip))

        val first = ctx.scheduleFor(trip.id!!)
        val second = ctx.scheduleFor(trip.id!!)

        assertThat(first).hasSize(1)
        assertThat(second).hasSize(1)
    }

    @Test
    fun `accumulates recommendations recorded across multiple calls`() {
        val (run, trip) = seedRun()
        val ctx = context(run, listOf(trip))
        val rowA = OptimizationRecommendationRow(run.id!!, OptimizationRecommendationKind.STOP_TIME, 5, 10, "a")
        val rowB = OptimizationRecommendationRow(run.id!!, OptimizationRecommendationKind.TRIP_SHIFT, 5, 10, "b")

        ctx.record(listOf(rowA))
        ctx.record(listOf(rowB))

        assertThat(ctx.recommendationsSoFar).hasSize(2)
        assertThat(ctx.recommendationsSoFar[0].reason).isEqualTo("a")
        assertThat(ctx.recommendationsSoFar[1].reason).isEqualTo("b")
    }
}
