package eu.transittrack.schedule.compare

import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

import eu.transittrack.gtfs.draft.DraftService
import eu.transittrack.gtfs.draft.edit.DeleteTripOp
import eu.transittrack.gtfs.draft.edit.DraftEditService
import eu.transittrack.gtfs.draft.edit.ShiftTripOp
import eu.transittrack.gtfs.feed.FeedInput
import eu.transittrack.gtfs.feed.GtfsFeedService
import eu.transittrack.gtfs.model.StopTimeRepository
import eu.transittrack.gtfs.revision.GtfsRevision
import eu.transittrack.gtfs.revision.GtfsRevisionRepository
import eu.transittrack.gtfs.support.IngestionTestFactory
import eu.transittrack.support.PostgresPerMethodTest

/** Ingestion + fork commit on their own connections — must not run inside a rollback tx, same as
 * [eu.transittrack.gtfs.draft.edit.TimeEditOpsTest]. */
@SpringBootTest(classes = [eu.transittrack.Application::class])
@Import(IngestionTestFactory::class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ScheduleComparisonServiceTest(
    @Autowired val comparisonService: ScheduleComparisonService,
    @Autowired val editService: DraftEditService,
    @Autowired val drafts: DraftService,
    @Autowired val revisions: GtfsRevisionRepository,
    @Autowired val stopTimes: StopTimeRepository,
    @Autowired val ingestFactory: IngestionTestFactory,
    @Autowired val feedService: GtfsFeedService,
) : PostgresPerMethodTest() {
    private fun forkDraft(): Pair<Long, Long> {
        val (feedCode, base) = ingestFactory.ingest("schedule-sample")
        val draft = drafts.fork(feedCode, null, "T", "alice")
        drafts.claimEditor(draft.id!!, "alice")
        return base to draft.id!!
    }

    private fun version(draftId: Long): Long = revisions.findById(draftId).orElseThrow().version

    private fun aTripId(revisionId: Long): String = stopTimes.findByRevisionId(revisionId).first().tripId

    @Test
    fun `unedited fork compares with no trip changes`() {
        val (base, draftId) = forkDraft()
        val cmp = comparisonService.compare(base, draftId)
        assertThat(cmp.tripChanges).isEmpty()
    }

    @Test
    fun `shifted trip reports STOP_TIME_CHANGED deltas`() {
        val (base, draftId) = forkDraft()
        val tripId = aTripId(draftId)
        editService.apply(draftId, "alice", version(draftId)) { _ -> ShiftTripOp(tripId, 120) }

        val cmp = comparisonService.compare(base, draftId)
        val change = cmp.tripChanges.single { it.tripId == tripId }
        assertThat(change.kind).isEqualTo(TripChangeKind.MODIFIED)
        assertThat(change.stopChanges.all { it.kind == StopChangeKind.STOP_TIME_CHANGED }).isEqualTo(true)
        assertThat(change.stopChanges.all { it.arrivalDeltaSec == 120 || it.arrivalDeltaSec == null }).isEqualTo(true)
    }

    @Test
    fun `deleted trip reports REMOVED with the base as from`() {
        val (base, draftId) = forkDraft()
        val tripId = aTripId(draftId)
        editService.apply(draftId, "alice", version(draftId)) { _ -> DeleteTripOp(tripId) }

        val cmp = comparisonService.compare(base, draftId)
        val change = cmp.tripChanges.single { it.tripId == tripId }
        assertThat(change.kind).isEqualTo(TripChangeKind.REMOVED)
        assertThat(change.stopChanges).isEmpty()
    }

    @Test
    fun `runTimeDeltaSec is null while derivation is stale`() {
        val (base, draftId) = forkDraft()
        val tripId = aTripId(draftId)
        editService.apply(draftId, "alice", version(draftId)) { _ -> ShiftTripOp(tripId, 120) }

        val cmp = comparisonService.compare(base, draftId)
        assertThat(cmp.toDerivationStale).isEqualTo(true)
        assertThat(cmp.tripChanges.single { it.tripId == tripId }.runTimeDeltaSec).isNull()
    }

    /**
     * `IngestionTestFactory.ingest` registers a feed by fixture code only on first use
     * (`feeds.findByCode(fixture) == null`), so two `ingest("schedule-sample")` calls within the
     * same test class reuse the same feed rather than creating distinct ones. To exercise the
     * cross-feed rejection we instead register a second, genuinely different feed and save a copy
     * of a real revision pointed at it (`gtfs_revision.feed_id` is FK-constrained, so the copy
     * must reference a feed row that actually exists), then compare against that.
     */
    @Test
    fun `comparing revisions from different feeds is rejected`() {
        val (base, _) = forkDraft()
        val baseRevision = revisions.findById(base).orElseThrow()
        val otherFeed =
            feedService.register(
                FeedInput("schedule-sample-other-${System.nanoTime()}", "OTHER", null, "http://x/g.zip", null),
            )
        val otherFeedRevision =
            revisions.save(
                GtfsRevision(otherFeed.id!!, baseRevision.status, baseRevision.sourceUrl),
            )

        try {
            comparisonService.compare(base, otherFeedRevision.id!!)
            error("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message ?: "").contains("different feeds").let { }
        }
    }
}
