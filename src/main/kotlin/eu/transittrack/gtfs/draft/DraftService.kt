package eu.transittrack.gtfs.draft

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

import eu.transittrack.gtfs.feed.GtfsFeedRepository
import eu.transittrack.gtfs.revision.GtfsRevision
import eu.transittrack.gtfs.revision.GtfsRevisionRepository
import eu.transittrack.gtfs.revision.GtfsRevisionStatus
import eu.transittrack.gtfs.revision.RevisionService

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
}
