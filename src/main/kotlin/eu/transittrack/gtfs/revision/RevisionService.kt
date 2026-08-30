package eu.transittrack.gtfs.revision

import eu.transittrack.gtfs.config.GtfsProperties
import eu.transittrack.gtfs.model.GtfsCalendarDateRepository
import eu.transittrack.gtfs.model.GtfsCalendarRepository
import eu.transittrack.gtfs.model.GtfsFeedInfoRepository
import eu.transittrack.gtfs.store.RevisionWriter
import java.time.Instant
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Lifecycle, activation and pruning of [GtfsRevision] rows.
 *
 * A revision walks `PENDING -> DOWNLOADING -> PARSING -> VALIDATING -> READY` and then
 * either `ACTIVE` (via [activate], which atomically supersedes the previous active
 * revision), `UNCHANGED` (content identical to the current active revision) or `FAILED`.
 */
@Service
class RevisionService(
    private val revisions: GtfsRevisionRepository,
    private val writer: RevisionWriter,
    private val props: GtfsProperties,
    private val calendars: GtfsCalendarRepository,
    private val calendarDates: GtfsCalendarDateRepository,
    private val feedInfos: GtfsFeedInfoRepository,
) {

    @Transactional
    fun createPending(feedId: Long, sourceUrl: String): GtfsRevision =
        revisions.save(
            GtfsRevision(feedId = feedId, status = GtfsRevisionStatus.PENDING, sourceUrl = sourceUrl),
        )

    @Transactional
    fun transition(
        revisionId: Long,
        status: GtfsRevisionStatus,
        apply: (GtfsRevision) -> Unit = {},
    ): GtfsRevision {
        val r = revision(revisionId)
        r.status = status
        apply(r)
        return revisions.save(r)
    }

    /** Wipe every row written for the revision, then record a terminal `FAILED` state. */
    @Transactional
    fun fail(revisionId: Long, message: String) {
        writer.deleteAllForRevision(revisionId)
        val r = revision(revisionId)
        r.status = GtfsRevisionStatus.FAILED
        r.errorMessage = message.take(4000)
        revisions.save(r)
    }

    @Transactional
    fun markUnchanged(revisionId: Long) {
        val r = revision(revisionId)
        r.status = GtfsRevisionStatus.UNCHANGED
        revisions.save(r)
    }

    /**
     * Populate `feedStartDate` / `feedEndDate` from `gtfs_feed_info` when present,
     * otherwise fall back to the span covered by `gtfs_calendar` and
     * `gtfs_calendar_date`.
     */
    @Transactional
    fun deriveDates(revisionId: Long) {
        val r = revision(revisionId)
        val info = feedInfos.findByRevisionId(revisionId)
        var start = info?.feedStartDate
        var end = info?.feedEndDate
        if (start == null || end == null) {
            val cals = calendars.findByRevisionId(revisionId)
            val exDates = calendarDates.findByRevisionId(revisionId).map { it.date }
            val allStarts = cals.mapNotNull { it.startDate } + exDates
            val allEnds = cals.mapNotNull { it.endDate } + exDates
            if (start == null) start = allStarts.minOrNull()
            if (end == null) end = allEnds.maxOrNull()
        }
        r.feedStartDate = start
        r.feedEndDate = end
        revisions.save(r)
    }

    /**
     * Atomically swap the active pointer for the feed: any current `ACTIVE`
     * revision becomes `SUPERSEDED`, then this revision becomes `ACTIVE`.
     */
    @Transactional
    fun activate(revisionId: Long): GtfsRevision {
        val r = revision(revisionId)
        revisions.findByFeedIdAndStatus(r.feedId, GtfsRevisionStatus.ACTIVE)?.let { current ->
            if (current.id != r.id) {
                current.status = GtfsRevisionStatus.SUPERSEDED
                current.supersededAt = Instant.now()
                // Flush the demotion before the promotion so the "one ACTIVE per feed"
                // partial unique index never sees two ACTIVE rows mid-transaction.
                revisions.saveAndFlush(current)
            }
        }
        r.status = GtfsRevisionStatus.ACTIVE
        r.activatedAt = Instant.now()
        return revisions.save(r)
    }

    /**
     * Keep the `ACTIVE` revision plus the [keep] most-recent other terminal revisions;
     * delete the rest (child rows via [RevisionWriter.deleteAllForRevision], then the
     * revision row). In-progress revisions are never touched. When trimming, `FAILED`
     * and `UNCHANGED` revisions are dropped ahead of `SUPERSEDED` ones, oldest first.
     */
    @Transactional
    fun prune(feedId: Long, keep: Int) {
        val all = revisions.findByFeedIdOrderByCreatedAtDesc(feedId)
        val terminal = all.filter { it.status != GtfsRevisionStatus.ACTIVE && it.status.terminal }
        // Prefer keeping SUPERSEDED over FAILED/UNCHANGED, and newer over older.
        val keepRank = terminal.sortedWith(
            compareByDescending<GtfsRevision> { it.status == GtfsRevisionStatus.SUPERSEDED }
                .thenByDescending { it.createdAt },
        )
        val keepIds = buildSet {
            all.firstOrNull { it.status == GtfsRevisionStatus.ACTIVE }?.id?.let(::add)
            keepRank.take(keep.coerceAtLeast(0)).forEach { it.id?.let(::add) }
        }
        val toDelete = terminal.filter { it.id !in keepIds }.sortedBy { it.createdAt }
        for (r in toDelete) {
            writer.deleteAllForRevision(r.id!!)
            revisions.delete(r)
        }
    }

    /** Delete every child row for the revision, then the revision row itself. */
    @Transactional
    fun deleteWithRows(revisionId: Long) {
        writer.deleteAllForRevision(revisionId)
        revisions.deleteById(revisionId)
    }

    fun activeRevisionId(feedId: Long): Long? =
        revisions.findByFeedIdAndStatus(feedId, GtfsRevisionStatus.ACTIVE)?.id

    fun hasInProgress(feedId: Long): Boolean =
        revisions.existsByFeedIdAndStatusIn(feedId, GtfsRevisionStatus.NON_TERMINAL_IN_PROGRESS)

    fun revision(id: Long): GtfsRevision =
        revisions.findById(id).orElseThrow()
}
