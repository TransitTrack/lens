@file:Suppress("ktlint:standard:max-line-length")

package eu.transittrack.gtfs.api

import java.time.LocalDate
import java.time.format.DateTimeParseException

import kotlinx.serialization.json.Json
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

import eu.transittrack.ConditionalOnRole
import eu.transittrack.Role
import eu.transittrack.gtfs.draft.DraftJobService
import eu.transittrack.gtfs.draft.DraftService
import eu.transittrack.gtfs.draft.edit.AddTripOp
import eu.transittrack.gtfs.draft.edit.BulkShiftTripsOp
import eu.transittrack.gtfs.draft.edit.DeleteTripOp
import eu.transittrack.gtfs.draft.edit.DraftEditService
import eu.transittrack.gtfs.draft.edit.DuplicateTripOp
import eu.transittrack.gtfs.draft.edit.EditOp
import eu.transittrack.gtfs.draft.edit.InsertTripStopOp
import eu.transittrack.gtfs.draft.edit.LockNotHeldException
import eu.transittrack.gtfs.draft.edit.NewStopTime
import eu.transittrack.gtfs.draft.edit.RemoveTripStopOp
import eu.transittrack.gtfs.draft.edit.ReorderTripStopsOp
import eu.transittrack.gtfs.draft.edit.SetCalendarExceptionOp
import eu.transittrack.gtfs.draft.edit.SetCalendarOp
import eu.transittrack.gtfs.draft.edit.SetStopDwellOp
import eu.transittrack.gtfs.draft.edit.ShiftTripOp
import eu.transittrack.gtfs.draft.edit.StaleDraftException
import eu.transittrack.gtfs.draft.edit.UpdateStopTimeOp
import eu.transittrack.gtfs.feed.GtfsFeedService
import eu.transittrack.gtfs.revision.GtfsRevision
import eu.transittrack.rest.RestRevisionMapper
import eu.transittrack.rest.generated.api.DraftsApi
import eu.transittrack.rest.generated.model.ActivateDraftRequest
import eu.transittrack.rest.generated.model.AddTripInput
import eu.transittrack.rest.generated.model.BulkShiftTripsInput
import eu.transittrack.rest.generated.model.ClaimDraftEditorRequest
import eu.transittrack.rest.generated.model.DeleteTripInput
import eu.transittrack.rest.generated.model.DiscardDraftRequest
import eu.transittrack.rest.generated.model.Draft
import eu.transittrack.rest.generated.model.DraftEdit
import eu.transittrack.rest.generated.model.DraftEditResult
import eu.transittrack.rest.generated.model.DraftJob
import eu.transittrack.rest.generated.model.DraftJobPhase
import eu.transittrack.rest.generated.model.DraftJobState
import eu.transittrack.rest.generated.model.DraftKind
import eu.transittrack.rest.generated.model.DraftLock
import eu.transittrack.rest.generated.model.DuplicateTripInput
import eu.transittrack.rest.generated.model.ForkDraftInput
import eu.transittrack.rest.generated.model.InsertTripStopInput
import eu.transittrack.rest.generated.model.Problem
import eu.transittrack.rest.generated.model.RedoDraftEditRequest
import eu.transittrack.rest.generated.model.RemoveTripStopInput
import eu.transittrack.rest.generated.model.ReorderTripStopsInput
import eu.transittrack.rest.generated.model.RevertDraftToForkRequest
import eu.transittrack.rest.generated.model.Revision
import eu.transittrack.rest.generated.model.RevisionStatus
import eu.transittrack.rest.generated.model.SetCalendarExceptionInput
import eu.transittrack.rest.generated.model.SetCalendarInput
import eu.transittrack.rest.generated.model.SetStopDwellInput
import eu.transittrack.rest.generated.model.Severity
import eu.transittrack.rest.generated.model.ShiftTripInput
import eu.transittrack.rest.generated.model.UndoDraftEditRequest
import eu.transittrack.rest.generated.model.UpdateStopTimeInput
import eu.transittrack.rest.generated.model.ValidationNotice
import eu.transittrack.rest.generated.model.ValidationSummary
import eu.transittrack.toScalarValue

@RestController
@RequestMapping("/api/v1")
@ConditionalOnRole(Role.API)
class DraftRestController(
    private val drafts: DraftService,
    private val edits: DraftEditService,
    private val jobs: DraftJobService,
    private val feeds: GtfsFeedService,
    private val revisionMapper: RestRevisionMapper,
    private val json: Json,
) : DraftsApi {
    @ExceptionHandler(StaleDraftException::class, LockNotHeldException::class)
    fun conflict(ex: RuntimeException): ResponseEntity<Problem> =
        ResponseEntity
            .status(HttpStatus.CONFLICT)
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(
                Problem(
                    title = "Conflict", status = 409, detail = ex.message,
                    code = if (ex is StaleDraftException) "STALE_DRAFT" else "LOCK_LOST",
                    currentVersion = (ex as? StaleDraftException)?.currentVersion,
                ),
            )

    @ExceptionHandler(IllegalArgumentException::class, ResponseStatusException::class)
    fun badRequest(ex: RuntimeException): ResponseEntity<Problem> =
        ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(Problem(title = "Bad Request", status = 400, detail = ex.message))

    override fun rebuildDraft(id: String): ResponseEntity<DraftJob> {
        drafts.get(id.toLong())
        val job = jobs.submitRebuild(id.toLong())
        return ResponseEntity.ok(DraftJob(job.id, DraftJobState.valueOf(job.state.name), DraftJobPhase.valueOf(job.phase.name), job.error))
    }

    override fun forkDraft(forkDraftInput: ForkDraftInput): ResponseEntity<Draft> {
        val i = forkDraftInput
        val rev = drafts.fork(i.feedCode, i.baseRevisionId?.toLong(), i.label, i.editor)
        drafts.claimEditor(rev.id!!, i.editor)
        return ResponseEntity.ok(draft(drafts.get(rev.id!!)))
    }

    override fun discardDraft(
        id: String,
        discardDraftRequest: DiscardDraftRequest,
    ): ResponseEntity<Boolean> {
        drafts.discard(id.toLong(), discardDraftRequest.editor)
        return ResponseEntity.ok(true)
    }

    override fun revertDraftToFork(
        id: String,
        revertDraftToForkRequest: RevertDraftToForkRequest,
    ): ResponseEntity<Draft> = ResponseEntity.ok(draft(drafts.revertToFork(id.toLong(), revertDraftToForkRequest.editor)))

    override fun activateDraft(
        id: String,
        activateDraftRequest: ActivateDraftRequest,
    ): ResponseEntity<Revision> {
        val rev = drafts.activate(id.toLong(), activateDraftRequest.editor, activateDraftRequest.force ?: false)
        return ResponseEntity.ok(revisionMapper.map(rev, feeds.codeOf(rev.feedId) ?: ""))
    }

    override fun claimDraftEditor(
        id: String,
        claimDraftEditorRequest: ClaimDraftEditorRequest,
    ): ResponseEntity<DraftLock> {
        val lock = drafts.claimEditor(id.toLong(), claimDraftEditorRequest.editor, claimDraftEditorRequest.takeOver ?: false)
        return ResponseEntity.ok(DraftLock(lock.editor, lock.expiresAt.toString()))
    }

    override fun renewDraftEditor(
        id: String,
        revertDraftToForkRequest: RevertDraftToForkRequest,
    ): ResponseEntity<DraftLock> {
        val lock = drafts.renewEditor(id.toLong(), revertDraftToForkRequest.editor)
        return ResponseEntity.ok(DraftLock(lock.editor, lock.expiresAt.toString()))
    }

    override fun releaseDraftEditor(
        id: String,
        revertDraftToForkRequest: RevertDraftToForkRequest,
    ): ResponseEntity<Boolean> = ResponseEntity.ok(drafts.releaseEditor(id.toLong(), revertDraftToForkRequest.editor))

    override fun undoDraftEdit(
        id: String,
        undoDraftEditRequest: UndoDraftEditRequest,
    ): ResponseEntity<DraftEditResult> =
        ResponseEntity.ok(result(edits.undo(id.toLong(), undoDraftEditRequest.editor, undoDraftEditRequest.expectedVersion)))

    override fun redoDraftEdit(
        id: String,
        redoDraftEditRequest: RedoDraftEditRequest,
    ): ResponseEntity<DraftEditResult> =
        ResponseEntity.ok(result(edits.redo(id.toLong(), redoDraftEditRequest.editor, redoDraftEditRequest.expectedVersion)))

    override fun updateStopTime(updateStopTimeInput: UpdateStopTimeInput): ResponseEntity<DraftEditResult> =
        updateStopTimeInput.let { i ->
            apply(i.draftId, i.editor, i.expectedVersion, UpdateStopTimeOp(i.tripId, i.stopSequence, i.arrivalSec, i.departureSec))
        }

    override fun shiftTrip(shiftTripInput: ShiftTripInput): ResponseEntity<DraftEditResult> =
        shiftTripInput.let { i -> apply(i.draftId, i.editor, i.expectedVersion, ShiftTripOp(i.tripId, i.deltaSec)) }

    override fun setStopDwell(setStopDwellInput: SetStopDwellInput): ResponseEntity<DraftEditResult> =
        setStopDwellInput.let { i -> apply(i.draftId, i.editor, i.expectedVersion, SetStopDwellOp(i.tripId, i.stopSequence, i.dwellSec)) }

    override fun addTrip(addTripInput: AddTripInput): ResponseEntity<DraftEditResult> =
        addTripInput.let { i ->
            apply(
                i.draftId, i.editor, i.expectedVersion,
                AddTripOp(
                    routeId = i.routeId, serviceId = i.serviceId, tripId = i.tripId, headsign = i.headsign,
                    directionId = i.directionId, shapeId = i.shapeId, blockId = i.blockId,
                    stops = i.stops.map { NewStopTime(it.stopId, it.arrivalSec, it.departureSec) },
                ),
            )
        }

    override fun duplicateTrip(duplicateTripInput: DuplicateTripInput): ResponseEntity<DraftEditResult> =
        duplicateTripInput.let { i ->
            apply(i.draftId, i.editor, i.expectedVersion, DuplicateTripOp(i.sourceTripId, i.newTripId, i.offsetSec))
        }

    override fun deleteTrip(deleteTripInput: DeleteTripInput): ResponseEntity<DraftEditResult> =
        deleteTripInput.let { i -> apply(i.draftId, i.editor, i.expectedVersion, DeleteTripOp(i.tripId)) }

    override fun bulkShiftTrips(bulkShiftTripsInput: BulkShiftTripsInput): ResponseEntity<DraftEditResult> =
        bulkShiftTripsInput.let { i ->
            apply(i.draftId, i.editor, i.expectedVersion, BulkShiftTripsOp(i.routeId, i.patternKey, i.serviceId, i.windowFromSec, i.windowToSec, i.deltaSec))
        }

    override fun insertTripStop(insertTripStopInput: InsertTripStopInput): ResponseEntity<DraftEditResult> =
        insertTripStopInput.let { i ->
            apply(i.draftId, i.editor, i.expectedVersion, InsertTripStopOp(i.tripId, i.afterStopSequence, i.stopId, i.arrivalSec, i.departureSec))
        }

    override fun removeTripStop(removeTripStopInput: RemoveTripStopInput): ResponseEntity<DraftEditResult> =
        removeTripStopInput.let { i -> apply(i.draftId, i.editor, i.expectedVersion, RemoveTripStopOp(i.tripId, i.stopSequence)) }

    override fun reorderTripStops(reorderTripStopsInput: ReorderTripStopsInput): ResponseEntity<DraftEditResult> =
        reorderTripStopsInput.let { i -> apply(i.draftId, i.editor, i.expectedVersion, ReorderTripStopsOp(i.tripId, i.stopIdOrder)) }

    override fun setCalendar(setCalendarInput: SetCalendarInput): ResponseEntity<DraftEditResult> =
        setCalendarInput.let { i ->
            apply(
                i.draftId, i.editor, i.expectedVersion,
                SetCalendarOp(
                    serviceId = i.serviceId, monday = i.monday, tuesday = i.tuesday, wednesday = i.wednesday,
                    thursday = i.thursday, friday = i.friday, saturday = i.saturday, sunday = i.sunday,
                    startDate = parseDate(i.startDate, "startDate"), endDate = parseDate(i.endDate, "endDate"),
                ),
            )
        }

    override fun setCalendarException(setCalendarExceptionInput: SetCalendarExceptionInput): ResponseEntity<DraftEditResult> =
        setCalendarExceptionInput.let { i ->
            apply(i.draftId, i.editor, i.expectedVersion, SetCalendarExceptionOp(i.serviceId, parseDate(i.date, "date"), i.exceptionType))
        }

    private fun apply(
        id: String,
        editor: String,
        version: Long,
        op: EditOp,
    ): ResponseEntity<DraftEditResult> = ResponseEntity.ok(result(edits.apply(id.toLong(), editor, version) { op }))

    private fun draft(d: GtfsRevision): Draft =
        Draft(
            id = d.id.toString(), feedCode = feeds.codeOf(d.feedId) ?: "", status = RevisionStatus.valueOf(d.status.name),
            kind = DraftKind.valueOf(d.kind.name), version = d.version, derivationStale = d.derivationStale,
            createdAt = d.createdAt.toString(), label = d.label, baseRevisionId = d.baseRevisionId?.toString(),
            lastValidation = d.lastValidation?.let { json.parseToJsonElement(it).toScalarValue() },
            lock = drafts.currentLock(d)?.let { DraftLock(it.editor, it.expiresAt.toString()) },
            createdBy = d.createdBy, rowCounts = d.rowCounts,
        )

    private fun result(r: DraftEditService.DraftEditResultData): DraftEditResult =
        DraftEditResult(
            draft(r.draft), r.canUndo, r.canRedo,
            r.edit?.let { DraftEdit(it.seq, it.op, it.summary, it.appliedAt.toString(), it.undone) },
        )

    private fun parseDate(
        value: String,
        field: String,
    ): LocalDate =
        try {
            LocalDate.parse(value)
        } catch (e: DateTimeParseException) {
            throw IllegalArgumentException("$field is not a valid ISO-8601 date: '$value'", e)
        }
}
