package eu.transittrack.gtfs.api

import java.time.DateTimeException
import java.time.LocalDate
import java.time.format.DateTimeParseException

import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.MutationMapping
import org.springframework.stereotype.Controller

import eu.transittrack.gtfs.api.dto.AddTripInput
import eu.transittrack.gtfs.api.dto.BulkShiftTripsInput
import eu.transittrack.gtfs.api.dto.DeleteTripInput
import eu.transittrack.gtfs.api.dto.DraftEditResultDto
import eu.transittrack.gtfs.api.dto.DuplicateTripInput
import eu.transittrack.gtfs.api.dto.InsertTripStopInput
import eu.transittrack.gtfs.api.dto.RemoveTripStopInput
import eu.transittrack.gtfs.api.dto.ReorderTripStopsInput
import eu.transittrack.gtfs.api.dto.SetCalendarExceptionInput
import eu.transittrack.gtfs.api.dto.SetCalendarInput
import eu.transittrack.gtfs.api.dto.SetStopDwellInput
import eu.transittrack.gtfs.api.dto.ShiftTripInput
import eu.transittrack.gtfs.api.dto.UpdateStopTimeInput
import eu.transittrack.gtfs.draft.DraftService
import eu.transittrack.gtfs.draft.edit.AddTripOp
import eu.transittrack.gtfs.draft.edit.BulkShiftTripsOp
import eu.transittrack.gtfs.draft.edit.DeleteTripOp
import eu.transittrack.gtfs.draft.edit.DraftEditService
import eu.transittrack.gtfs.draft.edit.DuplicateTripOp
import eu.transittrack.gtfs.draft.edit.InsertTripStopOp
import eu.transittrack.gtfs.draft.edit.NewStopTime
import eu.transittrack.gtfs.draft.edit.RemoveTripStopOp
import eu.transittrack.gtfs.draft.edit.ReorderTripStopsOp
import eu.transittrack.gtfs.draft.edit.SetCalendarExceptionOp
import eu.transittrack.gtfs.draft.edit.SetCalendarOp
import eu.transittrack.gtfs.draft.edit.SetStopDwellOp
import eu.transittrack.gtfs.draft.edit.ShiftTripOp
import eu.transittrack.gtfs.draft.edit.UpdateStopTimeOp
import eu.transittrack.gtfs.feed.GtfsFeedRepository
import eu.transittrack.gtfs.revision.GtfsRevision

@Controller
class DraftEditController(
    private val editService: DraftEditService,
    private val draftMapper: DraftMapper,
    private val draftService: DraftService,
    private val feeds: GtfsFeedRepository,
) {
    private fun feedCodeOf(rev: GtfsRevision): String = feeds.findById(rev.feedId).map { it.code }.orElse("")

    private fun toResult(r: DraftEditService.DraftEditResultData): DraftEditResultDto =
        DraftEditResultDto(
            draft = draftMapper.toDto(r.draft, feedCodeOf(r.draft), draftService.currentLock(r.draft)),
            edit = r.edit?.let(draftMapper::toDto),
            canUndo = r.canUndo,
            canRedo = r.canRedo,
        )

    /** `LocalDate.parse` throws [DateTimeParseException], which is not one of the resolver's generically
     * mapped types — rewrap as [IllegalArgumentException] here so a malformed date surfaces as `BAD_REQUEST`
     * like every other validation failure in this mutation, rather than `INTERNAL_ERROR`. */
    private fun parseDate(
        value: String,
        field: String,
    ): LocalDate =
        try {
            LocalDate.parse(value)
        } catch (e: DateTimeParseException) {
            throw IllegalArgumentException("$field is not a valid ISO-8601 date: '$value'", e)
        }

    @MutationMapping
    fun updateStopTime(
        @Argument input: UpdateStopTimeInput,
    ): DraftEditResultDto =
        toResult(
            editService.apply(input.draftId.toLong(), input.editor, input.expectedVersion) { _ ->
                UpdateStopTimeOp(input.tripId, input.stopSequence, input.arrivalSec, input.departureSec)
            },
        )

    @MutationMapping
    fun shiftTrip(
        @Argument input: ShiftTripInput,
    ): DraftEditResultDto =
        toResult(
            editService.apply(input.draftId.toLong(), input.editor, input.expectedVersion) { _ ->
                ShiftTripOp(input.tripId, input.deltaSec)
            },
        )

    @MutationMapping
    fun setStopDwell(
        @Argument input: SetStopDwellInput,
    ): DraftEditResultDto =
        toResult(
            editService.apply(input.draftId.toLong(), input.editor, input.expectedVersion) { _ ->
                SetStopDwellOp(input.tripId, input.stopSequence, input.dwellSec)
            },
        )

    @MutationMapping
    fun addTrip(
        @Argument input: AddTripInput,
    ): DraftEditResultDto =
        toResult(
            editService.apply(input.draftId.toLong(), input.editor, input.expectedVersion) { _ ->
                AddTripOp(
                    routeId = input.routeId,
                    serviceId = input.serviceId,
                    tripId = input.tripId,
                    headsign = input.headsign,
                    directionId = input.directionId,
                    shapeId = input.shapeId,
                    blockId = input.blockId,
                    stops = input.stops.map { NewStopTime(it.stopId, it.arrivalSec, it.departureSec) },
                )
            },
        )

    @MutationMapping
    fun duplicateTrip(
        @Argument input: DuplicateTripInput,
    ): DraftEditResultDto =
        toResult(
            editService.apply(input.draftId.toLong(), input.editor, input.expectedVersion) { _ ->
                DuplicateTripOp(input.sourceTripId, input.newTripId, input.offsetSec)
            },
        )

    @MutationMapping
    fun deleteTrip(
        @Argument input: DeleteTripInput,
    ): DraftEditResultDto =
        toResult(
            editService.apply(input.draftId.toLong(), input.editor, input.expectedVersion) { _ ->
                DeleteTripOp(input.tripId)
            },
        )

    @MutationMapping
    fun bulkShiftTrips(
        @Argument input: BulkShiftTripsInput,
    ): DraftEditResultDto =
        toResult(
            editService.apply(input.draftId.toLong(), input.editor, input.expectedVersion) { _ ->
                BulkShiftTripsOp(
                    input.routeId,
                    input.patternKey,
                    input.serviceId,
                    input.windowFromSec,
                    input.windowToSec,
                    input.deltaSec,
                )
            },
        )

    @MutationMapping
    fun insertTripStop(
        @Argument input: InsertTripStopInput,
    ): DraftEditResultDto =
        toResult(
            editService.apply(input.draftId.toLong(), input.editor, input.expectedVersion) { _ ->
                InsertTripStopOp(input.tripId, input.afterStopSequence, input.stopId, input.arrivalSec, input.departureSec)
            },
        )

    @MutationMapping
    fun removeTripStop(
        @Argument input: RemoveTripStopInput,
    ): DraftEditResultDto =
        toResult(
            editService.apply(input.draftId.toLong(), input.editor, input.expectedVersion) { _ ->
                RemoveTripStopOp(input.tripId, input.stopSequence)
            },
        )

    @MutationMapping
    fun reorderTripStops(
        @Argument input: ReorderTripStopsInput,
    ): DraftEditResultDto =
        toResult(
            editService.apply(input.draftId.toLong(), input.editor, input.expectedVersion) { _ ->
                ReorderTripStopsOp(input.tripId, input.stopIdOrder)
            },
        )

    @MutationMapping
    fun setCalendar(
        @Argument input: SetCalendarInput,
    ): DraftEditResultDto =
        toResult(
            editService.apply(input.draftId.toLong(), input.editor, input.expectedVersion) { _ ->
                SetCalendarOp(
                    serviceId = input.serviceId,
                    monday = input.monday, tuesday = input.tuesday, wednesday = input.wednesday,
                    thursday = input.thursday, friday = input.friday, saturday = input.saturday,
                    sunday = input.sunday,
                    startDate = parseDate(input.startDate, "startDate"),
                    endDate = parseDate(input.endDate, "endDate"),
                )
            },
        )

    @MutationMapping
    fun setCalendarException(
        @Argument input: SetCalendarExceptionInput,
    ): DraftEditResultDto =
        toResult(
            editService.apply(input.draftId.toLong(), input.editor, input.expectedVersion) { _ ->
                SetCalendarExceptionOp(input.serviceId, parseDate(input.date, "date"), input.exceptionType)
            },
        )

    @MutationMapping
    fun undoDraftEdit(
        @Argument id: String,
        @Argument editor: String,
        @Argument expectedVersion: Long,
    ): DraftEditResultDto = toResult(editService.undo(id.toLong(), editor, expectedVersion))

    @MutationMapping
    fun redoDraftEdit(
        @Argument id: String,
        @Argument editor: String,
        @Argument expectedVersion: Long,
    ): DraftEditResultDto = toResult(editService.redo(id.toLong(), editor, expectedVersion))
}
