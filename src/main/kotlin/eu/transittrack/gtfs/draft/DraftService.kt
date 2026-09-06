package eu.transittrack.gtfs.draft

import java.time.Instant

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

import eu.transittrack.gtfs.feed.GtfsFeedRepository
import eu.transittrack.gtfs.revision.GtfsRevision
import eu.transittrack.gtfs.revision.GtfsRevisionRepository
import eu.transittrack.gtfs.revision.GtfsRevisionStatus
import eu.transittrack.gtfs.revision.RevisionService

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
) {
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
        val held = currentLock(d)
        check(takeOver || held == null || held.editor == editor) { "locked by ${held?.editor}" }
        return writeClaim(d, editor)
    }

    @Transactional
    fun renewEditor(
        draftId: Long,
        editor: String,
    ): DraftLock {
        val d = get(draftId)
        check(currentLock(d)?.editor == editor) { "not the current editor" }
        return writeClaim(d, editor)
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

    private fun writeClaim(
        d: GtfsRevision,
        editor: String,
    ): DraftLock {
        val exp = Instant.now().plusSeconds(props.editorLeaseMinutes * 60)
        d.editorClaimBy = editor
        d.editorClaimExpiresAt = exp
        revisions.save(d)
        return DraftLock(editor, exp)
    }
}
