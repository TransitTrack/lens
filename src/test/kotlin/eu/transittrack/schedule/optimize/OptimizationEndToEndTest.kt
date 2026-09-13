package eu.transittrack.schedule.optimize

import java.time.Instant
import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotEmpty
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

import eu.transittrack.AvlAssignmentMode
import eu.transittrack.AvlFormat
import eu.transittrack.TestcontainersConfiguration
import eu.transittrack.avl.model.AvlFeed
import eu.transittrack.avl.model.AvlFeedRepository
import eu.transittrack.avl.model.AvlFeedSourceKind
import eu.transittrack.gtfs.draft.DraftJob
import eu.transittrack.gtfs.draft.DraftJobService
import eu.transittrack.gtfs.draft.DraftService
import eu.transittrack.gtfs.feed.GtfsFeedRepository
import eu.transittrack.gtfs.model.TripRepository
import eu.transittrack.gtfs.revision.GtfsRevisionRepository
import eu.transittrack.gtfs.revision.GtfsRevisionStatus
import eu.transittrack.gtfs.store.RevisionWriter
import eu.transittrack.gtfs.support.IngestionTestFactory
import eu.transittrack.predict.model.AvlStopCrossing
import eu.transittrack.predict.model.AvlStopCrossingRepository
import eu.transittrack.schedule.derive.ScheduleWriter
import eu.transittrack.schedule.model.ScheduleTimeRepository
import eu.transittrack.schedule.model.StopPathRepository
import eu.transittrack.schedule.optimize.model.OptimizationRunRepository
import eu.transittrack.schedule.optimize.model.OptimizationRunRow
import eu.transittrack.schedule.optimize.model.OptimizationRunState

/**
 * Whole-workflow acceptance test (design sections 5-9): ingests a real fixture, seeds genuine
 * `avl_stop_crossing` evidence for one of its trips (like [OptimizationAnalysisPipelineTest]'s
 * helpers, but against ingested rather than hand-built schedule rows), submits a real analysis run
 * through [ScheduleOptimizationService], applies the resulting recommendation, and rebuilds/validates
 * the returned draft through the existing draft workflow ([DraftJobService], the same one
 * [eu.transittrack.gtfs.draft.DraftJobServiceTest] drives) — asserting the applied draft is never
 * activated: it stays `DRAFT` through submission, rebuild, and validation.
 */
@SpringBootTest(classes = [eu.transittrack.Application::class])
@Import(TestcontainersConfiguration::class, IngestionTestFactory::class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OptimizationEndToEndTest(
    @Autowired val service: ScheduleOptimizationService,
    @Autowired val jobs: DraftJobService,
    @Autowired val drafts: DraftService,
    @Autowired val feeds: GtfsFeedRepository,
    @Autowired val revisions: GtfsRevisionRepository,
    @Autowired val trips: TripRepository,
    @Autowired val stopPaths: StopPathRepository,
    @Autowired val scheduleTimes: ScheduleTimeRepository,
    @Autowired val avlFeeds: AvlFeedRepository,
    @Autowired val crossings: AvlStopCrossingRepository,
    @Autowired val runs: OptimizationRunRepository,
    @Autowired val writer: RevisionWriter,
    @Autowired val scheduleWriter: ScheduleWriter,
    @Autowired val ingestFactory: IngestionTestFactory,
) {
    private val cleanRevisions = mutableListOf<Long>()
    private var cleanAvlFeedId: Long? = null

    @org.junit.jupiter.api.AfterEach
    fun cleanup() {
        cleanAvlFeedId?.let { runCatching { avlFeeds.deleteById(it) } }
        for (id in cleanRevisions) runCatching { scheduleWriter.deleteForRevision(id) }
        for (id in cleanRevisions) runCatching { writer.deleteAllForRevision(id) }
        for (id in cleanRevisions) runCatching { revisions.deleteById(id) }
        cleanRevisions.clear()
    }

    private fun awaitTerminal(runId: Long): OptimizationRunRow {
        val deadline = System.nanoTime() + 30_000_000_000L
        while (true) {
            val run = service.get(runId)!!
            if (run.state == OptimizationRunState.SUCCEEDED || run.state == OptimizationRunState.FAILED) return run
            check(System.nanoTime() < deadline) { "optimization run $runId did not settle within 30s (state=${run.state})" }
            Thread.sleep(50)
        }
    }

    @Test
    fun `ingest, capture crossings, analyze, apply, and rebuild - the applied draft never activates`() {
        val (feedCode, base) = ingestFactory.ingest("schedule-sample")
        cleanRevisions += base
        val feedId = feeds.findByCode(feedCode)!!.id!!

        // T1 (route RA, service WK, direction 0) departs S1 at 08:00:00 and is scheduled to reach
        // S2 (stop_sequence 2) at 08:10:00 - a 600s segment.
        val t1 = trips.findByRevisionId(base).single { it.tripId == "T1" }
        val patternId = t1.tripPatternId!!
        val segmentStopPathIndex = stopPaths.findByTripPatternOrdered(base, patternId).first { it.stopSeq == 2 }.stopPathIndex
        val scheduledSegmentSec =
            scheduleTimes.findByTripOrdered(base, t1.id!!).first { it.stopPathIndex == segmentStopPathIndex }.schedTravelTimeSec!!

        val avlFeed =
            avlFeeds.save(
                AvlFeed(
                    code = "avl-e2e-opt",
                    name = "AVL E2E OPT",
                    gtfsFeedCode = feedCode,
                    url = "http://x/avl",
                    format = AvlFormat.GTFS_RT,
                    pollIntervalSec = 30,
                    assignmentMode = AvlAssignmentMode.TRUST_DESCRIPTOR,
                    enabled = true,
                    headers = null,
                    source = AvlFeedSourceKind.CONFIG,
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                ),
            )
        cleanAvlFeedId = avlFeed.id!!

        // Two crossings consistently ~100s slower than scheduled - well past the default 30s
        // materiality threshold, and positive (nondecreasing), so it survives the pipeline's gate.
        val observedTravelTimeSec = (scheduledSegmentSec + 100).toDouble()
        val at = Instant.parse("2026-09-02T08:00:00Z")
        crossings.save(
            AvlStopCrossing(
                feedId = avlFeed.id!!, vehicleId = "v1", tripRowId = t1.id!!, tripPatternId = patternId,
                stopPathIndex = segmentStopPathIndex, observedAt = at, observedTravelTimeSec = observedTravelTimeSec,
                serviceId = t1.serviceId, tripStartSec = t1.startTimeSec!!, routeId = t1.routeId, directionId = t1.directionId ?: 0,
                revisionId = base,
            ),
        )
        crossings.save(
            AvlStopCrossing(
                feedId = avlFeed.id!!, vehicleId = "v1", tripRowId = t1.id!!, tripPatternId = patternId,
                stopPathIndex = segmentStopPathIndex, observedAt = at.plusSeconds(60), observedTravelTimeSec = observedTravelTimeSec,
                serviceId = t1.serviceId, tripStartSec = t1.startTimeSec!!, routeId = t1.routeId, directionId = t1.directionId ?: 0,
                revisionId = base,
            ),
        )

        // Restrict the eligible population to T1 alone (a tight departure-time window around its
        // 08:00:00 start) so the run proposes exactly one stop-time recommendation and no trip
        // shifts, keeping the apply step unambiguous.
        val run =
            service.submit(
                OptimizationRunRequest(
                    feedCode = feedCode,
                    serviceId = t1.serviceId,
                    routeId = t1.routeId,
                    directionId = t1.directionId,
                    windowFromSec = t1.startTimeSec!! - 300,
                    windowToSec = t1.startTimeSec!! + 300,
                    observedFrom = Instant.parse("2026-09-01T00:00:00Z"),
                    observedTo = Instant.parse("2026-09-08T00:00:00Z"),
                    minimumSamples = 2,
                ),
            )

        val settled = awaitTerminal(run.id!!)
        assertThat(settled.state).isEqualTo(OptimizationRunState.SUCCEEDED)

        val recommendations = service.listRecommendations(run.id!!, null, 0, 100)
        assertThat(recommendations).isNotEmpty()
        val pending = recommendations.first()

        val applied = service.apply(run.id!!, setOf(pending.id!!), "e2e-proposal", "planner")
        cleanRevisions += applied.id!!

        assertThat(applied.status).isEqualTo(GtfsRevisionStatus.DRAFT)
        assertThat(applied.derivationStale).isTrue()
        assertThat(applied.feedId).isEqualTo(feedId)
        assertThat(applied.baseRevisionId).isEqualTo(base)

        // Rebuild/validate through the existing draft workflow (design section 5's derive/validate
        // pipeline, exactly as eu.transittrack.gtfs.draft.DraftJobServiceTest drives it) - never
        // through activateDraft.
        val job = jobs.submitRebuild(applied.id!!)
        var waited = 0
        while (job.state == DraftJob.State.RUNNING && waited < 300) {
            Thread.sleep(100)
            waited++
        }
        assertThat(job.state).isEqualTo(DraftJob.State.SUCCEEDED)

        val rebuilt = revisions.findById(applied.id!!).orElseThrow()
        assertThat(rebuilt.derivationStale).isFalse()
        assertThat(rebuilt.lastValidation).isNotNull()

        // Never activated: the draft's status stays DRAFT through the entire submit/analyze/apply/
        // rebuild/validate pipeline - activateDraft is never called.
        assertThat(rebuilt.status).isEqualTo(GtfsRevisionStatus.DRAFT)
    }
}
