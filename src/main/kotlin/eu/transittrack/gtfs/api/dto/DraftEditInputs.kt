package eu.transittrack.gtfs.api.dto

/** GraphQL input types for the draft edit mutations (Task 6). */

data class UpdateStopTimeInput(
    val draftId: String,
    val editor: String,
    val expectedVersion: Long,
    val tripId: String,
    val stopSequence: Int,
    val arrivalSec: Int? = null,
    val departureSec: Int? = null,
)

data class ShiftTripInput(
    val draftId: String,
    val editor: String,
    val expectedVersion: Long,
    val tripId: String,
    val deltaSec: Int,
)

data class SetStopDwellInput(
    val draftId: String,
    val editor: String,
    val expectedVersion: Long,
    val tripId: String,
    val stopSequence: Int,
    val dwellSec: Int,
)

data class NewStopTimeInput(
    val stopId: String,
    val arrivalSec: Int,
    val departureSec: Int,
)

data class AddTripInput(
    val draftId: String,
    val editor: String,
    val expectedVersion: Long,
    val routeId: String,
    val serviceId: String,
    val tripId: String? = null,
    val headsign: String? = null,
    val directionId: Int? = null,
    val shapeId: String? = null,
    val blockId: String? = null,
    val stops: List<NewStopTimeInput> = emptyList(),
)

data class DuplicateTripInput(
    val draftId: String,
    val editor: String,
    val expectedVersion: Long,
    val sourceTripId: String,
    val newTripId: String? = null,
    val offsetSec: Int,
)

data class DeleteTripInput(
    val draftId: String,
    val editor: String,
    val expectedVersion: Long,
    val tripId: String,
)

data class BulkShiftTripsInput(
    val draftId: String,
    val editor: String,
    val expectedVersion: Long,
    val routeId: String? = null,
    val patternKey: String? = null,
    val serviceId: String? = null,
    val windowFromSec: Int? = null,
    val windowToSec: Int? = null,
    val deltaSec: Int,
)

data class InsertTripStopInput(
    val draftId: String,
    val editor: String,
    val expectedVersion: Long,
    val tripId: String,
    val afterStopSequence: Int,
    val stopId: String,
    val arrivalSec: Int? = null,
    val departureSec: Int? = null,
)

data class RemoveTripStopInput(
    val draftId: String,
    val editor: String,
    val expectedVersion: Long,
    val tripId: String,
    val stopSequence: Int,
)

data class ReorderTripStopsInput(
    val draftId: String,
    val editor: String,
    val expectedVersion: Long,
    val tripId: String,
    val stopIdOrder: List<String> = emptyList(),
)

data class DraftEditResultDto(
    val draft: DraftDto,
    val edit: DraftEditDto?,
    val canUndo: Boolean,
    val canRedo: Boolean,
)
