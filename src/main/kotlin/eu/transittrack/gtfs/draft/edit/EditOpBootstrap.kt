package eu.transittrack.gtfs.draft.edit

import jakarta.annotation.PostConstruct

import org.springframework.stereotype.Component

/**
 * Kotlin runs a `companion object { init { … } }` only when the class is first referenced. Undo/redo
 * can replay an op that has not been used yet this JVM run, so we force every op's companion `init`
 * (its [EditOpRegistry] registration) at startup by naming the class as an expression.
 *
 * Tasks 3-5 add more ops (AddTripOp, DuplicateTripOp, DeleteTripOp, BulkShiftTripsOp,
 * InsertTripStopOp, RemoveTripStopOp, ReorderTripStopsOp) — extend the list below as they land.
 */
@Component
class EditOpBootstrap {
    @PostConstruct
    @Suppress("UNUSED_EXPRESSION")
    fun boot() {
        UpdateStopTimeOp
        ShiftTripOp
        SetStopDwellOp
        AddTripOp
        DuplicateTripOp
        DeleteTripOp
        BulkShiftTripsOp
        InsertTripStopOp
        RemoveTripStopOp
        ReorderTripStopsOp
        SetCalendarOp
        SetCalendarExceptionOp
    }
}
