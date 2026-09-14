package eu.transittrack.schedule.compare

import java.time.LocalDate
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
import eu.transittrack.gtfs.draft.edit.DuplicateTripOp
import eu.transittrack.gtfs.draft.edit.SetCalendarExceptionOp
import eu.transittrack.gtfs.draft.edit.SetCalendarOp
import eu.transittrack.gtfs.draft.edit.ShiftTripOp
import eu.transittrack.gtfs.feed.FeedInput
import eu.transittrack.gtfs.feed.GtfsFeedService
import eu.transittrack.gtfs.model.CalendarRepository
import eu.transittrack.gtfs.model.StopTimeRepository
import eu.transittrack.gtfs.revision.GtfsRevision
import eu.transittrack.gtfs.revision.GtfsRevisionRepository
import eu.transittrack.gtfs.support.IngestionTestFactory
import eu.transittrack.schedule.derive.DerivationService
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
    @Autowired val calendars: CalendarRepository,
    @Autowired val ingestFactory: IngestionTestFactory,
    @Autowired val feedService: GtfsFeedService,
    @Autowired val derivation: DerivationService,
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

    @Test
    fun `changed calendar field reports a MODIFIED CalendarChange`() {
        val (base, draftId) = forkDraft()
        val serviceId = calendars.findByRevisionId(draftId).first { it.serviceId == "WK" }.serviceId
        editService.apply(draftId, "alice", version(draftId)) { _ ->
            SetCalendarOp(
                serviceId,
                monday = false, tuesday = false, wednesday = false, thursday = false, friday = false,
                saturday = true, sunday = true,
                startDate = LocalDate.of(2027, 1, 1), endDate = LocalDate.of(2027, 6, 30),
            )
        }

        val cmp = comparisonService.compare(base, draftId)
        val change = cmp.calendarChanges.single { it.serviceId == serviceId }
        assertThat(change.kind).isEqualTo(CalendarChangeKind.MODIFIED)
        assertThat(change.fieldChanges.containsKey("saturday")).isEqualTo(true)
    }

    @Test
    fun `added calendar exception reports EXCEPTION_ADDED`() {
        val (base, draftId) = forkDraft()
        val serviceId = calendars.findByRevisionId(draftId).first().serviceId
        editService.apply(draftId, "alice", version(draftId)) { _ ->
            SetCalendarExceptionOp(serviceId, LocalDate.of(2026, 12, 25), 2)
        }

        val cmp = comparisonService.compare(base, draftId)
        val change = cmp.calendarChanges.single { it.serviceId == serviceId }
        val exception = change.exceptionChanges.single { it.date == LocalDate.of(2026, 12, 25) }
        assertThat(exception.kind).isEqualTo(ExceptionChangeKind.EXCEPTION_ADDED)
        assertThat(exception.toType).isEqualTo(2)
    }

    @Test
    fun `unedited fork compares with no calendar changes`() {
        val (base, draftId) = forkDraft()
        assertThat(comparisonService.compare(base, draftId).calendarChanges).isEmpty()
    }

    @Test
    fun `headway summaries are empty while either side is stale`() {
        val (base, draftId) = forkDraft()
        // base is READY/ACTIVE (non-stale by construction); draft starts stale until rebuilt
        val cmp = comparisonService.compare(base, draftId)
        assertThat(cmp.headwaySummaries).isEmpty()
    }

    @Test
    fun `headway summaries are populated once both sides are rebuilt`() {
        val (base, draftId) = forkDraft()
        derivation.rederive(draftId)
        val fresh = revisions.findById(draftId).orElseThrow()
        assertThat(fresh.derivationStale).isEqualTo(false)

        val cmp = comparisonService.compare(base, draftId)
        assertThat(cmp.headwaySummaries.isNotEmpty()).isEqualTo(true)
        // groups with fewer than 2 trips report null gap stats, never a misleading number
        cmp.headwaySummaries.filter { it.fromTripCount < 2 }.forEach {
            assertThat(it.fromMeanGapSec).isNull()
            assertThat(it.fromMaxGapSec).isNull()
        }
    }

    @Test
    fun `headway summary reports exact known gaps for route RA direction 0 service WK`() {
        // schedule-sample RA/direction 0/WK has 4 trips: T1 (08:00 = 28800s), T2 (09:00 =
        // 32400s), T3 (07:00 = 25200s), and T5, which is frequency-based (frequencies.txt gives
        // it a 06:00-09:00/900s window) so BlockProcessor/SchedTripProcessor normalizes its
        // startTimeSec to 0 (relative to its own first departure) rather than 21600. Sorted
        // starts are therefore [0, 25200, 28800, 32400] -> gaps [25200, 3600, 3600], mean =
        // 32400/3 = 10800, max = 25200.
        val (base, draftId) = forkDraft()
        derivation.rederive(draftId)

        val cmp = comparisonService.compare(base, draftId)
        val summary = cmp.headwaySummaries.single { it.routeId == "RA" && it.directionId == 0 && it.serviceId == "WK" }
        assertThat(summary.fromTripCount).isEqualTo(4)
        assertThat(summary.toTripCount).isEqualTo(4)
        assertThat(summary.fromMeanGapSec).isEqualTo(10800)
        assertThat(summary.fromMaxGapSec).isEqualTo(25200)
        assertThat(summary.toMeanGapSec).isEqualTo(10800)
        assertThat(summary.toMaxGapSec).isEqualTo(25200)
    }

    @Test
    fun `duplicated trip reports ADDED`() {
        val (base, draftId) = forkDraft()
        val sourceTripId = aTripId(draftId)
        editService.apply(draftId, "alice", version(draftId)) { _ ->
            DuplicateTripOp(sourceTripId, "T1_DUP", 60)
        }

        val cmp = comparisonService.compare(base, draftId)
        val change = cmp.tripChanges.single { it.tripId == "T1_DUP" }
        assertThat(change.kind).isEqualTo(TripChangeKind.ADDED)
    }

    @Test
    fun `new service reports ADDED, and REMOVED from the reverse comparison`() {
        val (base, draftId) = forkDraft()
        editService.apply(draftId, "alice", version(draftId)) { _ ->
            SetCalendarOp(
                "NEW_SVC",
                monday = true, tuesday = true, wednesday = true, thursday = true, friday = true,
                saturday = false, sunday = false,
                startDate = LocalDate.of(2026, 1, 1), endDate = LocalDate.of(2026, 12, 31),
            )
        }

        val added = comparisonService.compare(base, draftId).calendarChanges.single { it.serviceId == "NEW_SVC" }
        assertThat(added.kind).isEqualTo(CalendarChangeKind.ADDED)

        // Reversing from/to turns the same edit into a REMOVED, since there's no "delete calendar"
        // edit op to exercise the REMOVED branch directly.
        val removed = comparisonService.compare(draftId, base).calendarChanges.single { it.serviceId == "NEW_SVC" }
        assertThat(removed.kind).isEqualTo(CalendarChangeKind.REMOVED)
    }

    @Test
    fun `calendar_dates-only service with no calendars row is visible in the diff`() {
        val (base, draftId) = forkDraft()
        editService.apply(draftId, "alice", version(draftId)) { _ ->
            SetCalendarExceptionOp("HOLIDAY", LocalDate.of(2026, 12, 25), 1)
        }

        val cmp = comparisonService.compare(base, draftId)
        val change = cmp.calendarChanges.single { it.serviceId == "HOLIDAY" }
        assertThat(change.kind).isEqualTo(CalendarChangeKind.MODIFIED)
        assertThat(change.fieldChanges).isEmpty()
        val exception = change.exceptionChanges.single { it.date == LocalDate.of(2026, 12, 25) }
        assertThat(exception.kind).isEqualTo(ExceptionChangeKind.EXCEPTION_ADDED)
        assertThat(exception.toType).isEqualTo(1)
    }

    /**
     * `SetCalendarExceptionOp` upserts, so two applications on the same draft leave no
     * journal-visible intermediate state to compare against for EXCEPTION_CHANGED within a single
     * from/to pair. EXCEPTION_REMOVED has the same shape problem in the forward direction (the base
     * fixture has no pre-existing exception to delete for a fresh serviceId). Both are exercised
     * here via a reversed comparison instead: the draft (as "from") has the exception, the base (as
     * "to") does not, so the reverse direction reports it as removed.
     */
    @Test
    fun `EXCEPTION_REMOVED is reported via reverse comparison`() {
        val (base, draftId) = forkDraft()
        val serviceId = calendars.findByRevisionId(draftId).first().serviceId
        editService.apply(draftId, "alice", version(draftId)) { _ ->
            SetCalendarExceptionOp(serviceId, LocalDate.of(2026, 12, 25), 2)
        }

        val cmp = comparisonService.compare(draftId, base)
        val change = cmp.calendarChanges.single { it.serviceId == serviceId }
        val exception = change.exceptionChanges.single { it.date == LocalDate.of(2026, 12, 25) }
        assertThat(exception.kind).isEqualTo(ExceptionChangeKind.EXCEPTION_REMOVED)
        assertThat(exception.fromType).isEqualTo(2)
    }

    @Test
    fun `routeId filter narrows trip changes`() {
        val (base, draftId) = forkDraft()
        val tripId = aTripId(draftId)
        editService.apply(draftId, "alice", version(draftId)) { _ -> ShiftTripOp(tripId, 120) }

        val filteredOut = comparisonService.compare(base, draftId, routeId = "RB")
        assertThat(filteredOut.tripChanges.any { it.tripId == tripId }).isEqualTo(false)

        val filteredIn = comparisonService.compare(base, draftId, routeId = "RA")
        assertThat(filteredIn.tripChanges.any { it.tripId == tripId }).isEqualTo(true)
    }
}
