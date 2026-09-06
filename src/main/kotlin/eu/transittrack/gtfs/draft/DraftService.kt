package eu.transittrack.gtfs.draft

import java.time.Instant

import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.json.JsonMapper

import eu.transittrack.gtfs.feed.GtfsFeedRepository
import eu.transittrack.gtfs.revision.GtfsRevision
import eu.transittrack.gtfs.revision.GtfsRevisionRepository
import eu.transittrack.gtfs.revision.GtfsRevisionStatus
import eu.transittrack.gtfs.revision.RevisionService
import eu.transittrack.gtfs.store.RevisionWriter
import eu.transittrack.schedule.derive.ScheduleWriter

data class DraftLock(
    val editor: String,
    val expiresAt: Instant,
)

/**
 * Forks a base revision's raw GTFS tables into an isolated `DRAFT` revision an editor can modify,
 * and lists / fetches those drafts.
 */
@Service
class DraftService(
    private val revisions: GtfsRevisionRepository,
    private val revisionService: RevisionService,
    private val copier: DraftRowCopier,
    private val edits: DraftEditRepository,
    private val feeds: GtfsFeedRepository,
    private val props: DraftProperties,
    private val revisionWriter: RevisionWriter,
    scheduleWriterProvider: ObjectProvider<ScheduleWriter>,
    private val objectMapper: JsonMapper,
) {
    // ScheduleWriter is @ConditionalOnProperty(transittrack.schedule.enabled) — absent when the
    // schedule feature is toggled off, so the draft feature must degrade rather than fail startup.
    private val scheduleWriter: ScheduleWriter? = scheduleWriterProvider.ifAvailable

    @Transactional
    fun fork(
        feedCode: String,
        baseRevisionId: Long?,
        label: String?,
        createdBy: String?,
    ): GtfsRevision {
        val feed = feeds.findByCode(feedCode) ?: throw IllegalArgumentException("no feed '$feedCode'")
        val base =
            when {
                baseRevisionId != null -> {
                    revisions.findById(baseRevisionId).orElseThrow {
                        IllegalArgumentException("no revision $baseRevisionId")
                    }
                }

                else -> {
                    revisions.findByFeedAndStatus(feed.id!!, GtfsRevisionStatus.ACTIVE)
                        ?: throw IllegalArgumentException("feed '$feedCode' has no ACTIVE revision to fork")
                }
            }
        if (base.feedId != feed.id) {
            throw IllegalArgumentException("base revision ${base.id} belongs to a different feed")
        }

        var draft =
            revisions.saveAndFlush(
                GtfsRevision(
                    feedId = feed.id!!,
                    status = GtfsRevisionStatus.DRAFT,
                    sourceUrl = base.sourceUrl,
                ).apply {
                    kind = DraftKind.DRAFT
                    this.baseRevisionId = base.id
                    this.label = label
                    this.createdBy = createdBy
                    derivationStale = true
                    feedStartDate = base.feedStartDate
                    feedEndDate = base.feedEndDate
                },
            )
        val counts = copier.copyRawTables(base.id!!, draft.id!!)
        draft.rowCounts = counts
        draft = revisions.save(draft)
        return draft
    }

    fun listDrafts(feedCode: String?): List<GtfsRevision> {
        val all =
            revisions.findByStatus(GtfsRevisionStatus.DRAFT) +
                revisions.findByKindAndStatus(DraftKind.DRAFT, GtfsRevisionStatus.ACTIVE)
        val filtered =
            if (feedCode == null) {
                all
            } else {
                val feedId = feeds.findByCode(feedCode)?.id ?: return emptyList()
                all.filter { it.feedId == feedId }
            }
        return filtered.sortedByDescending { it.createdAt }
    }

    fun get(draftId: Long): GtfsRevision {
        val r = revisions.findById(draftId).orElseThrow { IllegalArgumentException("no draft $draftId") }
        if (r.kind != DraftKind.DRAFT) {
            throw IllegalArgumentException("revision $draftId is not a draft")
        }
        return r
    }

    fun currentLock(draft: GtfsRevision): DraftLock? {
        val who = draft.editorClaimBy ?: return null
        val exp = draft.editorClaimExpiresAt ?: return null
        return if (exp.isAfter(Instant.now())) DraftLock(who, exp) else null
    }

    @Transactional
    fun claimEditor(
        draftId: Long,
        editor: String,
        takeOver: Boolean = false,
    ): DraftLock {
        val d = get(draftId)
        check(d.status == GtfsRevisionStatus.DRAFT) { "draft $draftId is not editable (status ${d.status})" }
        // The claim is now a single conditional UPDATE (see writeClaim) — the check-then-write race
        // where two clients both saw a free lock is gone.
        return writeClaim(d, editor, takeOver)
    }

    @Transactional
    fun renewEditor(
        draftId: Long,
        editor: String,
    ): DraftLock {
        val d = get(draftId)
        check(d.status == GtfsRevisionStatus.DRAFT) { "draft $draftId is not editable (status ${d.status})" }
        check(currentLock(d)?.editor == editor) { "not the current editor" }
        return writeClaim(d, editor, takeOver = false)
    }

    @Transactional
    fun releaseEditor(
        draftId: Long,
        editor: String,
    ): Boolean {
        val d = get(draftId)
        if (d.editorClaimBy != editor) return false
        d.editorClaimBy = null
        d.editorClaimExpiresAt = null
        revisions.save(d)
        return true
    }

    @Transactional
    fun discard(draftId: Long) {
        val d = get(draftId)
        check(d.status == GtfsRevisionStatus.DRAFT) { "only DRAFT revisions can be discarded" }
        wipeRows(draftId)
        edits.deleteByRevisionId(draftId)
        revisions.deleteById(draftId)
    }

    @Transactional
    fun revertToFork(draftId: Long): GtfsRevision {
        val d = get(draftId)
        check(d.status == GtfsRevisionStatus.DRAFT) { "only DRAFT revisions can be reverted" }
        val base = d.baseRevisionId ?: error("draft $draftId has no base revision")
        wipeRows(draftId)
        edits.deleteByRevisionId(draftId)
        // wipeRows commits on ScheduleWriter/RevisionWriter's own connections; the entity-field
        // updates below are in this JPA transaction. If the re-copy throws, that wipe still stands,
        // so persist an honest "empty draft, must re-revert" state (in a REQUIRES_NEW txn that
        // survives this rollback) rather than let `drafts`/`draft` keep advertising the old counts.
        val counts =
            try {
                copier.copyRawTables(base, draftId)
            } catch (e: RuntimeException) {
                revisionService.markRevertFailed(draftId)
                throw e
            }
        d.rowCounts = counts
        d.version += 1
        d.derivationStale = true
        d.lastValidation = null
        return revisions.save(d)
    }

    @Transactional
    fun activate(
        draftId: Long,
        force: Boolean = false,
    ): GtfsRevision {
        val d = get(draftId)
        check(d.status == GtfsRevisionStatus.DRAFT) { "only DRAFT revisions can be activated" }
        if (!force) {
            check(!d.derivationStale) { "rebuild the draft before activating" }
            val errors =
                d.lastValidation
                    ?.let { objectMapper.readTree(it).path("errorCount").asInt(0) } ?: -1
            check(errors == 0) { "draft has $errors validation error(s); rebuild & validate, or force" }
        }
        return revisionService.activate(draftId)
    }

    private fun wipeRows(revisionId: Long) {
        scheduleWriter?.deleteForRevision(revisionId)
        revisionWriter.deleteAllForRevision(revisionId)
    }

    private fun writeClaim(
        d: GtfsRevision,
        editor: String,
        takeOver: Boolean,
    ): DraftLock {
        val now = Instant.now()
        val exp = now.plusSeconds(props.editorLeaseMinutes * 60)
        // Atomic: sets the holder only when the lock is free / expired / self / takeOver.
        val updated = revisions.tryClaimEditor(d.id!!, editor, exp, now, takeOver)
        check(updated == 1) { "locked by ${d.editorClaimBy}" }
        return DraftLock(editor, exp)
    }
}
