package eu.transittrack.gtfs.draft.edit

import java.time.Instant

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.json.JsonMapper

import eu.transittrack.gtfs.draft.DraftEdit
import eu.transittrack.gtfs.draft.DraftEditRepository
import eu.transittrack.gtfs.draft.DraftKind
import eu.transittrack.gtfs.draft.DraftService
import eu.transittrack.gtfs.model.CalendarDateRepository
import eu.transittrack.gtfs.model.CalendarRepository
import eu.transittrack.gtfs.model.FrequencyRepository
import eu.transittrack.gtfs.model.StopTimeRepository
import eu.transittrack.gtfs.model.TripRepository
import eu.transittrack.gtfs.revision.GtfsRevision
import eu.transittrack.gtfs.revision.GtfsRevisionRepository
import eu.transittrack.gtfs.revision.GtfsRevisionStatus
import eu.transittrack.schedule.model.TripPatternRepository

/**
 * The shared template for every draft edit: guard (status + lock + version) -> plan -> in one tx
 * mutate + journal one `draft_edit` row + bump version + mark derivation stale + truncate the redo
 * tail. `undo` / `redo` replay the stored inverse / forward direction through [EditOpRegistry].
 */
@Service
class DraftEditService(
    private val revisions: GtfsRevisionRepository,
    private val edits: DraftEditRepository,
    private val draftService: DraftService,
    private val stopTimes: StopTimeRepository,
    private val trips: TripRepository,
    private val frequencies: FrequencyRepository,
    private val tripPatterns: TripPatternRepository,
    private val calendars: CalendarRepository,
    private val calendarDates: CalendarDateRepository,
    private val json: JsonMapper,
) {
    data class DraftEditResultData(
        val draft: GtfsRevision,
        val edit: DraftEdit?,
        val canUndo: Boolean,
        val canRedo: Boolean,
    )

    private fun ctx(revisionId: Long) = EditContext(revisionId, stopTimes, trips, frequencies, tripPatterns, calendars, calendarDates, json)

    fun editsFor(revisionId: Long): List<DraftEdit> = edits.findByRevisionIdOrderBySeqAsc(revisionId)

    private fun guard(
        draftId: Long,
        editor: String,
        expectedVersion: Long,
    ): GtfsRevision {
        // Pessimistic row lock: serializes concurrent apply/undo/redo on one draft so the version
        // check below is a real compare-and-swap (a plain findById leaves a check-then-act window
        // where two concurrent `undo`s both pass and double-apply a delta inverse).
        val d = revisions.findByIdForUpdate(draftId) ?: throw IllegalArgumentException("no draft $draftId")
        require(d.kind == DraftKind.DRAFT && d.status == GtfsRevisionStatus.DRAFT) { "not an editable draft" }
        if (draftService.currentLock(d)?.editor != editor) throw LockNotHeldException(d.id!!)
        if (d.version != expectedVersion) throw StaleDraftException(d.version)
        return d
    }

    private fun result(
        d: GtfsRevision,
        edit: DraftEdit?,
    ) = DraftEditResultData(
        d,
        edit,
        canUndo = edits.findTopByRevisionIdAndUndoneFalseOrderBySeqDesc(d.id!!) != null,
        canRedo = edits.findTopByRevisionIdAndUndoneTrueOrderBySeqAsc(d.id!!) != null,
    )

    private fun bump(d: GtfsRevision) {
        d.version += 1
        d.derivationStale = true
        revisions.save(d)
    }

    /**
     * The shared batch template: guard (status + lock + version) ONCE, then plan + mutate + journal
     * + bump version for each op in [operations] in sequence, all within one transaction. Each op
     * still becomes its own `draft_edit` row (so it remains individually undoable), and each bump
     * mirrors the single-op `apply`'s per-edit version semantics exactly — just looped.
     */
    @Transactional
    fun applyBatch(
        draftId: Long,
        editor: String,
        expectedVersion: Long,
        operations: List<EditOp>,
    ): GtfsRevision = applyBatchInternal(draftId, editor, expectedVersion, operations)

    private fun applyBatchInternal(
        draftId: Long,
        editor: String,
        expectedVersion: Long,
        operations: List<EditOp>,
    ): GtfsRevision {
        val d = guard(draftId, editor, expectedVersion)
        val c = ctx(draftId)
        for (op in operations) {
            check(!(op.needsFreshDerivation && d.derivationStale)) { "rebuild the draft before filtering by pattern" }
            val planned = op.plan(c)

            edits.deleteByRevisionIdAndUndoneTrue(draftId) // truncate redo tail
            planned.mutate()

            // Ordering is safe: the findByRevisionIdOrderBySeqAsc query below forces a Hibernate flush,
            // and within one flush Hibernate runs INSERTs before DELETEs — so the queued redo-tail
            // deletes are applied before nextSeq is computed, and it cannot collide with
            // uq_draft_edit_revision_seq. A future `select max(seq)` projection must keep that property:
            // it must stay a query over draft_edit (which auto-flushes), not an in-memory shortcut.
            val nextSeq = (edits.findByRevisionIdOrderBySeqAsc(draftId).maxOfOrNull { it.seq } ?: 0) + 1
            edits.save(
                DraftEdit(
                    revisionId = draftId,
                    seq = nextSeq,
                    op = op.op,
                    summary = planned.summary,
                    forward = json.writeValueAsString(planned.forward),
                    inverse = json.writeValueAsString(planned.inverse),
                    appliedAt = Instant.now(),
                ),
            )
            bump(d)
        }
        return d
    }

    @Transactional
    fun apply(
        draftId: Long,
        editor: String,
        expectedVersion: Long,
        opFactory: (EditContext) -> EditOp,
    ): DraftEditResultData {
        val op = opFactory(ctx(draftId))
        val d = applyBatchInternal(draftId, editor, expectedVersion, listOf(op))
        return result(d, edits.findTopByRevisionIdAndUndoneFalseOrderBySeqDesc(draftId))
    }

    @Transactional
    fun undo(
        draftId: Long,
        editor: String,
        expectedVersion: Long,
    ): DraftEditResultData {
        val d = guard(draftId, editor, expectedVersion)
        val top = edits.findTopByRevisionIdAndUndoneFalseOrderBySeqDesc(draftId) ?: return result(d, null)
        EditOpRegistry.mutationFor(top.op, json.readTree(top.inverse))(ctx(draftId))
        top.undone = true
        edits.save(top)
        bump(d)
        return result(d, top)
    }

    @Transactional
    fun redo(
        draftId: Long,
        editor: String,
        expectedVersion: Long,
    ): DraftEditResultData {
        val d = guard(draftId, editor, expectedVersion)
        val next = edits.findTopByRevisionIdAndUndoneTrueOrderBySeqAsc(draftId) ?: return result(d, null)
        EditOpRegistry.mutationFor(next.op, json.readTree(next.forward))(ctx(draftId))
        next.undone = false
        edits.save(next)
        bump(d)
        return result(d, next)
    }
}
