package eu.transittrack.gtfs.draft.edit

import java.time.Instant

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.json.JsonMapper

import eu.transittrack.gtfs.draft.DraftEdit
import eu.transittrack.gtfs.draft.DraftEditRepository
import eu.transittrack.gtfs.draft.DraftKind
import eu.transittrack.gtfs.draft.DraftService
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
    private val jdbc: JdbcTemplate,
    private val json: JsonMapper,
) {
    data class DraftEditResultData(
        val draft: GtfsRevision,
        val edit: DraftEdit?,
        val canUndo: Boolean,
        val canRedo: Boolean,
    )

    private fun ctx(revisionId: Long) = EditContext(revisionId, stopTimes, trips, frequencies, tripPatterns, jdbc, json)

    private fun guard(
        draftId: Long,
        editor: String,
        expectedVersion: Long,
    ): GtfsRevision {
        val d = revisions.findById(draftId).orElseThrow { IllegalArgumentException("no draft $draftId") }
        require(d.kind == DraftKind.DRAFT && d.status == GtfsRevisionStatus.DRAFT) { "not an editable draft" }
        check(draftService.currentLock(d)?.editor == editor) { "you are not the current editor" }
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

    @Transactional
    fun apply(
        draftId: Long,
        editor: String,
        expectedVersion: Long,
        opFactory: (EditContext) -> EditOp,
    ): DraftEditResultData {
        val d = guard(draftId, editor, expectedVersion)
        val c = ctx(draftId)
        val op = opFactory(c)
        check(!(op.needsFreshDerivation && d.derivationStale)) { "rebuild the draft before filtering by pattern" }
        val planned = op.plan(c)

        edits.deleteByRevisionIdAndUndoneTrue(draftId) // truncate redo tail
        planned.mutate()

        val nextSeq = (edits.findByRevisionIdOrderBySeqAsc(draftId).maxOfOrNull { it.seq } ?: 0) + 1
        val row =
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
        return result(d, row)
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
