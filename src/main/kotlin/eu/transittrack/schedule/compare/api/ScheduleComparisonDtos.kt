package eu.transittrack.schedule.compare.api

data class ScheduleComparisonDto(
    val fromRevisionId: String,
    val toRevisionId: String,
    val fromDerivationStale: Boolean,
    val toDerivationStale: Boolean,
    val tripChangeCount: Int,
    val tripChanges: List<TripChangeDto>,
    val calendarChanges: List<CalendarChangeDto>,
    val headwaySummaries: List<HeadwaySummaryDto>,
)

data class TripChangeDto(
    val tripId: String,
    val kind: String,
    val fieldChanges: Map<String, Map<String, String?>>,
    val stopChanges: List<StopTimeChangeDto>,
    val runTimeDeltaSec: Int?,
)

data class StopTimeChangeDto(
    val stopSequence: Int,
    val kind: String,
    val stopId: String?,
    val arrivalDeltaSec: Int?,
    val departureDeltaSec: Int?,
)

data class CalendarChangeDto(
    val serviceId: String,
    val kind: String,
    val fieldChanges: Map<String, Map<String, String?>>,
    val exceptionChanges: List<CalendarExceptionChangeDto>,
)

data class CalendarExceptionChangeDto(
    val date: String,
    val kind: String,
    val fromType: Int?,
    val toType: Int?,
)

data class HeadwaySummaryDto(
    val routeId: String,
    val directionId: Int?,
    val serviceId: String,
    val fromTripCount: Int,
    val toTripCount: Int,
    val fromMeanGapSec: Int?,
    val toMeanGapSec: Int?,
    val fromMaxGapSec: Int?,
    val toMaxGapSec: Int?,
)
