package eu.transittrack.schedule.optimize

import java.time.Instant
import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import org.springframework.beans.factory.annotation.Autowired
import tools.jackson.databind.json.JsonMapper

import eu.transittrack.AvlAssignmentMode
import eu.transittrack.AvlFormat
import eu.transittrack.avl.model.AvlFeed
import eu.transittrack.avl.model.AvlFeedRepository
import eu.transittrack.avl.model.AvlFeedSourceKind
import eu.transittrack.gtfs.feed.FeedSource
import eu.transittrack.gtfs.feed.GtfsFeed
import eu.transittrack.gtfs.feed.GtfsFeedRepository
import eu.transittrack.gtfs.model.Trip
import eu.transittrack.gtfs.model.TripRepository
import eu.transittrack.gtfs.revision.GtfsRevision
import eu.transittrack.gtfs.revision.GtfsRevisionRepository
import eu.transittrack.gtfs.revision.GtfsRevisionStatus
import eu.transittrack.gtfs.support.PostgresSliceTest
import eu.transittrack.predict.PredictionAlgorithm
import eu.transittrack.predict.model.AvlStopCrossing
import eu.transittrack.predict.model.AvlStopCrossingRepository
import eu.transittrack.predict.model.PredictionAccuracy
import eu.transittrack.predict.model.PredictionAccuracyRepository
import eu.transittrack.schedule.model.ScheduleTime
import eu.transittrack.schedule.model.ScheduleTimeRepository
import eu.transittrack.schedule.model.StopPath
import eu.transittrack.schedule.model.StopPathRepository
import eu.transittrack.schedule.model.TripPattern
import eu.transittrack.schedule.model.TripPatternRepository
import eu.transittrack.schedule.optimize.model.OptimizationRunRepository
import eu.transittrack.schedule.optimize.model.OptimizationRunRow
import eu.transittrack.support.PostgresPerMethodTest

@PostgresSliceTest
class StopTimeAnalyzerTest(
    @Autowired val feeds: GtfsFeedRepository,
    @Autowired val revisions: GtfsRevisionRepository,
    @Autowired val avlFeeds: AvlFeedRepository,
    @Autowired val patterns: TripPatternRepository,
    @Autowired val stopPaths: StopPathRepository,
    @Autowired val trips: TripRepository,
    @Autowired val scheduleTimes: ScheduleTimeRepository,
    @Autowired val crossings: AvlStopCrossingRepository,
    @Autowired val accuracies: PredictionAccuracyRepository,
    @Autowired val runs: OptimizationRunRepository,
) : PostgresPerMethodTest() {
    private val json = JsonMapper.builder().build()
    private val analyzer = StopTimeAnalyzer(OptimizationProperties(), json)

    private fun seedFeedAndRevision(code: String = "g"): Triple<Long, Long, Long> {
        val feed =
            feeds.save(
                GtfsFeed(code, "G", null, "http://x/z.zip", null, true, null, FeedSource.API, Instant.now(), Instant.now()),
            )
        val revision =
            revisions.save(GtfsRevision(feedId = feed.id!!, status = GtfsRevisionStatus.READY, sourceUrl = "http://x/z.zip"))
        val avlFeed =
            avlFeeds.save(
                AvlFeed(
                    code = "avl-$code", name = "AVL $code", gtfsFeedCode = code, url = "http://x/avl",
                    format = AvlFormat.GTFS_RT, pollIntervalSec = 30, assignmentMode = AvlAssignmentMode.TRUST_DESCRIPTOR,
                    enabled = true, headers = null, source = AvlFeedSourceKind.CONFIG, createdAt = Instant.now(), updatedAt = Instant.now(),
                ),
            )
        return Triple(feed.id!!, revision.id!!, avlFeed.id!!)
    }

    private fun pattern(
        revisionId: Long,
        routeId: String = "R1",
    ) = patterns.save(
        TripPattern(
            revisionId = revisionId,
            patternKey = "SHP|S1_to_S2|$routeId",
            routeId = routeId,
            routeShortName = null,
            directionId = 0,
            headsign = "To S2",
            shapeId = "SHP",
            stopCount = 2,
            lengthM = 1000.0,
            tripCount = 1,
        ),
    )

    private fun stopPathsFor(
        revisionId: Long,
        patternId: Long,
    ) {
        stopPaths.save(
            StopPath(
                revisionId, patternId, 0, "S1", "R1", 1, 0.0, null, null, null, waitStop = true,
                scheduleAdherenceStop = true, layoverStop = true, breakTimeSec = null,
            ),
        )
        stopPaths.save(
            StopPath(
                revisionId, patternId, 1, "S2", "R1", 2, 1000.0, null, null, null, waitStop = false,
                scheduleAdherenceStop = true, layoverStop = false, breakTimeSec = null,
            ),
        )
    }

    private fun stopPathsFor3(
        revisionId: Long,
        patternId: Long,
    ) {
        stopPaths.save(
            StopPath(
                revisionId, patternId, 0, "S1", "R1", 1, 0.0, null, null, null, waitStop = true,
                scheduleAdherenceStop = true, layoverStop = true, breakTimeSec = null,
            ),
        )
        stopPaths.save(
            StopPath(
                revisionId, patternId, 1, "S2", "R1", 2, 1000.0, null, null, null, waitStop = false,
                scheduleAdherenceStop = true, layoverStop = false, breakTimeSec = null,
            ),
        )
        stopPaths.save(
            StopPath(
                revisionId, patternId, 2, "S3", "R1", 3, 2000.0, null, null, null, waitStop = false,
                scheduleAdherenceStop = true, layoverStop = false, breakTimeSec = null,
            ),
        )
    }

    private fun trip(
        revisionId: Long,
        patternId: Long,
        tripId: String,
        routeId: String,
        serviceId: String,
        startTimeSec: Int,
    ) = trips.save(
        Trip(
            revisionId = revisionId, routeId = routeId, serviceId = serviceId, tripId = tripId, tripHeadsign = null,
            tripShortName = null, directionId = 0, blockId = null, shapeId = "SHP", wheelchairAccessible = null,
            bikesAllowed = null, tripPatternId = patternId, startTimeSec = startTimeSec, endTimeSec = startTimeSec + 1000,
            frequencyBased = false, noSchedule = false,
        ),
    )

    private fun scheduleTimesFor(
        revisionId: Long,
        tripRowId: Long,
        startTimeSec: Int,
        segmentTravelSec: Int,
    ) {
        scheduleTimes.save(
            ScheduleTime(
                revisionId, tripRowId, 0, startTimeSec, startTimeSec, interpolated = false,
                schedTravelTimeSec = null, schedDwellTimeSec = 0,
            ),
        )
        scheduleTimes.save(
            ScheduleTime(
                revisionId, tripRowId, 1, startTimeSec + segmentTravelSec, startTimeSec + segmentTravelSec,
                interpolated = false, schedTravelTimeSec = segmentTravelSec, schedDwellTimeSec = 0,
            ),
        )
    }

    private fun scheduleTimesWithInterpolatedMiddle(
        revisionId: Long,
        tripRowId: Long,
        startTimeSec: Int,
        segmentTravelSec: Int,
    ) {
        scheduleTimes.save(
            ScheduleTime(
                revisionId, tripRowId, 0, startTimeSec, startTimeSec, interpolated = false,
                schedTravelTimeSec = null, schedDwellTimeSec = 0,
            ),
        )
        scheduleTimes.save(
            ScheduleTime(
                revisionId, tripRowId, 1, startTimeSec + segmentTravelSec, startTimeSec + segmentTravelSec,
                interpolated = true, schedTravelTimeSec = segmentTravelSec, schedDwellTimeSec = 0,
            ),
        )
        scheduleTimes.save(
            ScheduleTime(
                revisionId, tripRowId, 2, startTimeSec + 2 * segmentTravelSec,
                startTimeSec + 2 * segmentTravelSec, interpolated = false,
                schedTravelTimeSec = segmentTravelSec, schedDwellTimeSec = 0,
            ),
        )
    }

    private fun crossing(
        feedId: Long,
        tripRowId: Long,
        patternId: Long,
        revisionId: Long,
        stopPathIndex: Int,
        observedTravelTimeSec: Double,
        observedAt: Instant,
        serviceId: String,
        routeId: String,
        tripStartSec: Int,
    ) = crossings.save(
        AvlStopCrossing(
            feedId = feedId,
            vehicleId = "v1",
            tripRowId = tripRowId,
            tripPatternId = patternId,
            stopPathIndex = stopPathIndex,
            observedAt = observedAt,
            observedTravelTimeSec = observedTravelTimeSec,
            serviceId = serviceId,
            tripStartSec = tripStartSec,
            routeId = routeId,
            directionId = 0,
            revisionId = revisionId,
        ),
    )

    private fun baseRun(
        feedId: Long,
        revisionId: Long,
        serviceId: String = "WK",
        routeId: String = "R1",
        minimumSamples: Int = 2,
    ) = runs.save(
        OptimizationRunRow(
            feedId = feedId, revisionId = revisionId, serviceId = serviceId, routeId = routeId, directionId = 0,
            observedFrom = Instant.parse("2026-09-01T00:00:00Z"), observedTo = Instant.parse("2026-09-08T00:00:00Z"),
            minimumSamples = minimumSamples,
        ),
    )

    private fun contextFor(
        run: OptimizationRunRow,
        eligibleTrips: List<Trip>,
        avlFeedIds: List<Long>,
    ) = OptimizationAnalysisContext(
        run = run,
        eligibleTrips = eligibleTrips,
        avlFeedIdsSupplier = { avlFeedIds },
        crossingsSupplier = {
            val eligibleTripRowIds = eligibleTrips.mapNotNull { it.id }.toSet()
            crossings
                .findForAnalysis(
                    avlFeedIds, run.revisionId, run.observedFrom, run.observedTo, run.serviceId, run.routeId,
                    run.directionId, run.windowFromSec, run.windowToSec,
                ).filter { it.tripRowId in eligibleTripRowIds }
        },
        scheduleTimes = scheduleTimes,
        stopPaths = stopPaths,
        predictionAccuracy = accuracies,
    )

    @Test
    fun `proposes a stop-time recommendation from robust median with prediction evidence, excluding a nonselected trip`() {
        val (feedId, revisionId, avlFeedId) = seedFeedAndRevision()
        val tp = pattern(revisionId)
        stopPathsFor(revisionId, tp.id!!)

        val selected = trip(revisionId, tp.id!!, "T1", "R1", "WK", 28_800)
        scheduleTimesFor(revisionId, selected.id!!, 28_800, segmentTravelSec = 300)

        val other = trip(revisionId, tp.id!!, "T2", "R1", "OTHER", 30_000)
        scheduleTimesFor(revisionId, other.id!!, 30_000, segmentTravelSec = 300)

        val at = Instant.parse("2026-09-02T08:00:00Z")
        crossing(avlFeedId, selected.id!!, tp.id!!, revisionId, 1, 360.0, at, "WK", "R1", 28_800)
        crossing(avlFeedId, selected.id!!, tp.id!!, revisionId, 1, 370.0, at.plusSeconds(60), "WK", "R1", 28_800)
        crossing(avlFeedId, selected.id!!, tp.id!!, revisionId, 1, 9_999.0, at.plusSeconds(120), "WK", "R1", 28_800)
        crossing(avlFeedId, other.id!!, tp.id!!, revisionId, 1, 900.0, at, "OTHER", "R1", 30_000)

        accuracies.save(
            PredictionAccuracy(
                avlFeedId, "v1", selected.id!!, 1, PredictionAlgorithm.SCHEDULE_ADHERENCE,
                at, at.plusSeconds(5), errorSec = 5, absErrorSec = 5, createdAt = at,
            ),
        )
        accuracies.save(
            PredictionAccuracy(
                avlFeedId, "v1", selected.id!!, 1, PredictionAlgorithm.SCHEDULE_ADHERENCE,
                at.plusSeconds(60), at.plusSeconds(55), errorSec = -5, absErrorSec = 5,
                createdAt = at.plusSeconds(60),
            ),
        )

        val run = baseRun(feedId, revisionId)
        val eligibleTrips = listOf(selected)
        val result = analyzer.analyze(contextFor(run, eligibleTrips, listOf(avlFeedId)))

        assertThat(result).hasSize(1)
        val recommendation = result.single()
        assertThat(recommendation.deltaSec).isEqualTo(65)
        assertThat(recommendation.sampleCount).isEqualTo(2)
        assertThat(recommendation.evidence!!).contains("predictionMeanAbsErrorSec")
        assertThat(recommendation.currentValue!!).contains("T1")
        assertThat(recommendation.currentValue!!.contains("T2")).isFalse()
        assertThat(recommendation.conflictKey!!).isEqualTo("cell:T1:2")
    }

    @Test
    fun `does not propose a stop-time recommendation from undersampled evidence`() {
        val (feedId, revisionId, avlFeedId) = seedFeedAndRevision()
        val tp = pattern(revisionId)
        stopPathsFor(revisionId, tp.id!!)
        val selected = trip(revisionId, tp.id!!, "T1", "R1", "WK", 28_800)
        scheduleTimesFor(revisionId, selected.id!!, 28_800, segmentTravelSec = 300)

        val at = Instant.parse("2026-09-02T08:00:00Z")
        crossing(avlFeedId, selected.id!!, tp.id!!, revisionId, 1, 360.0, at, "WK", "R1", 28_800)

        val run = baseRun(feedId, revisionId, minimumSamples = 2)
        val result = analyzer.analyze(contextFor(run, listOf(selected), listOf(avlFeedId)))

        assertThat(result).isEmpty()
    }

    @Test
    fun `discards a stop-time candidate that would make a trip's cumulative times decrease`() {
        val (feedId, revisionId, avlFeedId) = seedFeedAndRevision()
        val tp = pattern(revisionId)
        stopPathsFor(revisionId, tp.id!!)
        val selected = trip(revisionId, tp.id!!, "T1", "R1", "WK", 28_800)
        scheduleTimesFor(revisionId, selected.id!!, 28_800, segmentTravelSec = 40)

        val at = Instant.parse("2026-09-02T08:00:00Z")
        crossing(avlFeedId, selected.id!!, tp.id!!, revisionId, 1, 5.0, at, "WK", "R1", 28_800)
        crossing(avlFeedId, selected.id!!, tp.id!!, revisionId, 1, 5.0, at.plusSeconds(60), "WK", "R1", 28_800)

        val run = baseRun(feedId, revisionId)
        val result = analyzer.analyze(contextFor(run, listOf(selected), listOf(avlFeedId)))

        assertThat(result).isEmpty()
    }

    @Test
    fun `omits an interpolated cell from a stop-time recommendation's targets while keeping downstream explicit cells`() {
        val (feedId, revisionId, avlFeedId) = seedFeedAndRevision()
        val tp = pattern(revisionId)
        stopPathsFor3(revisionId, tp.id!!)

        val selected = trip(revisionId, tp.id!!, "T1", "R1", "WK", 28_800)
        scheduleTimesWithInterpolatedMiddle(revisionId, selected.id!!, 28_800, segmentTravelSec = 300)

        val at = Instant.parse("2026-09-02T08:00:00Z")
        crossing(avlFeedId, selected.id!!, tp.id!!, revisionId, 1, 360.0, at, "WK", "R1", 28_800)
        crossing(avlFeedId, selected.id!!, tp.id!!, revisionId, 1, 370.0, at.plusSeconds(60), "WK", "R1", 28_800)

        val run = baseRun(feedId, revisionId)
        val result = analyzer.analyze(contextFor(run, listOf(selected), listOf(avlFeedId)))

        assertThat(result).hasSize(1)
        val recommendation = result.single()
        assertThat(recommendation.currentValue!!).contains("\"stopSequence\":3")
        assertThat(recommendation.proposedValue!!).contains("\"stopSequence\":3")
        assertThat(recommendation.currentValue!!.contains("\"stopSequence\":2")).isFalse()
        assertThat(recommendation.proposedValue!!.contains("\"stopSequence\":2")).isFalse()
    }

    @Test
    fun `produces nothing and does not throw when no AVL feed is mapped`() {
        val (feedId, revisionId, _) = seedFeedAndRevision()
        val tp = pattern(revisionId)
        stopPathsFor(revisionId, tp.id!!)
        val selected = trip(revisionId, tp.id!!, "T1", "R1", "WK", 28_800)
        scheduleTimesFor(revisionId, selected.id!!, 28_800, segmentTravelSec = 300)

        val run = baseRun(feedId, revisionId)
        val result = analyzer.analyze(contextFor(run, listOf(selected), emptyList()))

        assertThat(result).isEmpty()
    }
}
