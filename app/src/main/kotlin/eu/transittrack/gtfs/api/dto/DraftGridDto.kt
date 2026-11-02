package eu.transittrack.gtfs.api.dto

data class DraftGridDto(
    val stops: List<DraftGridStopDto>,
    val trips: List<DraftGridTripDto>,
)

data class DraftGridStopDto(
    val stopSequence: Int,
    val stopId: String?,
    val stopName: String?,
    val timepoint: Int?,
)

data class DraftGridTripDto(
    val tripId: String,
    val firstDepartureSec: Int?,
    val headsign: String?,
    val blockId: String?,
    val directionId: Int?,
    val serviceId: String?,
    val cells: List<DraftGridCellDto>,
)

data class DraftGridCellDto(
    val stopSequence: Int,
    val arrivalSec: Int?,
    val departureSec: Int?,
)
