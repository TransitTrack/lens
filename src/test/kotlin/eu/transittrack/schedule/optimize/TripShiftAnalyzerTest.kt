package eu.transittrack.schedule.optimize

import java.time.Instant
import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
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
import eu.transittrack.schedule.optimize.model.OptimizationRecommendationKind
import eu.transittrack.schedule.optimize.model.OptimizationRecommendationRow
import eu.transittrack.schedule.optimize.model.OptimizationRunRepository
import eu.transittrack.schedule.optimize.model.OptimizationRunRow
import eu.transittrack.support.PostgresPerMethodTest

@PostgresSliceTest
class TripShiftAnalyzerTest(
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
    private val analyzer = TripShiftAnalyzer(OptimizationProperties(), json)

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
                revisionId, patternId, 0, "S1", "R1", 1, 0.0, null, null, null,
                waitStop = true, scheduleAdherenceStop = true, layoverStop = true, breakTimeSec = null,
            ),
        )
        stopPaths.save(
            StopPath(
                revisionId, patternId, 1, "S2", "R1", 2, 1000.0, null, null, null,
                waitStop = false, scheduleAdherenceStop = true, layoverStop = false, breakTimeSec = null,
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
                revisionId, tripRowId, 0, startTimeSec, startTimeSec,
                interpolated = false, schedTravelTimeSec = null, schedDwellTimeSec = 0,
            ),
        )
        scheduleTimes.save(
            ScheduleTime(
                revisionId, tripRowId, 1, startTimeSec + segmentTravelSec, startTimeSec + segmentTravelSec,
                interpolated = false, schedTravelTimeSec = segmentTravelSec, schedDwellTimeSec = 0,
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
            feedId = feedId, vehicleId = "v1", tripRowId = tripRowId, tripPatternId = patternId,
            stopPathIndex = stopPathIndex, observedAt = observedAt, observedTravelTimeSec = observedTravelTimeSec,
            serviceId = serviceId, tripStartSec = tripStartSec, routeId = routeId, directionId = 0, revisionId = revisionId,
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
        alreadyRecorded: List<OptimizationRecommendationRow> = emptyList(),
    ): OptimizationAnalysisContext {
        val ctx =
            OptimizationAnalysisContext(
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
        ctx.record(alreadyRecorded)
        return ctx
    }

    @Test
    fun `proposes an order-preserving trip shift that reduces headway deviation among uneven gaps`() {
        val (feedId, revisionId, avlFeedId) = seedFeedAndRevision()
        val tp = pattern(revisionId)
        stopPathsFor(revisionId, tp.id!!)

        val t1 = trip(revisionId, tp.id!!, "T1", "R1", "WK", 0)
        scheduleTimesFor(revisionId, t1.id!!, 0, segmentTravelSec = 200)
        val t2 = trip(revisionId, tp.id!!, "T2", "R1", "WK", 1_000)
        scheduleTimesFor(revisionId, t2.id!!, 1_000, segmentTravelSec = 200)
        val t3 = trip(revisionId, tp.id!!, "T3", "R1", "WK", 1_700)
        scheduleTimesFor(revisionId, t3.id!!, 1_700, segmentTravelSec = 200)

        val at = Instant.parse("2026-09-02T08:00:00Z")
        crossing(avlFeedId, t2.id!!, tp.id!!, revisionId, 1, 48.0, at, "WK", "R1", 1_000)
        crossing(avlFeedId, t2.id!!, tp.id!!, revisionId, 1, 52.0, at.plusSeconds(60), "WK", "R1", 1_000)
        accuracies.save(
            PredictionAccuracy(
                avlFeedId, "v1", t2.id!!, 1, PredictionAlgorithm.SCHEDULE_ADHERENCE, at, at.plusSeconds(3),
                errorSec = 3, absErrorSec = 3, createdAt = at,
            ),
        )

        val run = baseRun(feedId, revisionId)
        val result = analyzer.analyze(contextFor(run, listOf(t1, t2, t3), listOf(avlFeedId)))

        assertThat(result).hasSize(1)
        val recommendation = result.single()
        assertThat(recommendation.deltaSec).isEqualTo(-150)
        assertThat(recommendation.conflictKey!!).isEqualTo("shift:T2")
        assertThat(recommendation.proposedValue!!).contains("850")
        assertThat(recommendation.evidence!!).contains("\"predictionMeanAbsErrorSec\":3.0")
    }

    @Test
    fun `rejects a trip shift that would cross an adjacent trip, storing nothing`() {
        val (feedId, revisionId, avlFeedId) = seedFeedAndRevision()
        val tp = pattern(revisionId)
        stopPathsFor(revisionId, tp.id!!)

        val t1 = trip(revisionId, tp.id!!, "T1", "R1", "WK", 3_300)
        scheduleTimesFor(revisionId, t1.id!!, 3_300, segmentTravelSec = 200)
        val t2 = trip(revisionId, tp.id!!, "T2", "R1", "WK", 3_700)
        scheduleTimesFor(revisionId, t2.id!!, 3_700, segmentTravelSec = 200)
        val t3 = trip(revisionId, tp.id!!, "T3", "R1", "WK", 3_900)
        scheduleTimesFor(revisionId, t3.id!!, 3_900, segmentTravelSec = 200)

        val at = Instant.parse("2026-09-02T08:00:00Z")
        crossing(avlFeedId, t2.id!!, tp.id!!, revisionId, 1, -250.0, at, "WK", "R1", 3_700)
        crossing(avlFeedId, t2.id!!, tp.id!!, revisionId, 1, -240.0, at.plusSeconds(60), "WK", "R1", 3_700)

        val run = baseRun(feedId, revisionId)
        val result = analyzer.analyze(contextFor(run, listOf(t1, t2, t3), listOf(avlFeedId)))

        assertThat(result).isEmpty()
    }

    @Test
    fun `skips a trip already targeted by a stop-time recommendation recorded earlier in the run`() {
        val (feedId, revisionId, avlFeedId) = seedFeedAndRevision()
        val tp = pattern(revisionId)
        stopPathsFor(revisionId, tp.id!!)

        val t1 = trip(revisionId, tp.id!!, "T1", "R1", "WK", 0)
        scheduleTimesFor(revisionId, t1.id!!, 0, segmentTravelSec = 200)
        val t2 = trip(revisionId, tp.id!!, "T2", "R1", "WK", 1_000)
        scheduleTimesFor(revisionId, t2.id!!, 1_000, segmentTravelSec = 200)
        val t3 = trip(revisionId, tp.id!!, "T3", "R1", "WK", 1_700)
        scheduleTimesFor(revisionId, t3.id!!, 1_700, segmentTravelSec = 200)

        val at = Instant.parse("2026-09-02T08:00:00Z")
        crossing(avlFeedId, t2.id!!, tp.id!!, revisionId, 1, 48.0, at, "WK", "R1", 1_000)
        crossing(avlFeedId, t2.id!!, tp.id!!, revisionId, 1, 52.0, at.plusSeconds(60), "WK", "R1", 1_000)

        val run = baseRun(feedId, revisionId)
        val fakeStopTimeRow =
            OptimizationRecommendationRow(
                runId = run.id!!,
                kind = OptimizationRecommendationKind.STOP_TIME,
                sampleCount = 2,
                deltaSec = 10,
                reason = "test",
                proposedValue = json.writeValueAsString(mapOf("targets" to listOf(mapOf("tripId" to "T2")))),
            )
        val result = analyzer.analyze(contextFor(run, listOf(t1, t2, t3), listOf(avlFeedId), alreadyRecorded = listOf(fakeStopTimeRow)))

        assertThat(result).isEmpty()
    }
}
