package eu.transittrack.schedule.compare.api

import eu.transittrack.schedule.compare.CalendarChangeKind
import eu.transittrack.schedule.compare.ExceptionChangeKind
import eu.transittrack.schedule.compare.StopChangeKind
import eu.transittrack.schedule.compare.TripChangeKind

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
    val kind: TripChangeKind,
    val fieldChanges: Map<String, Map<String, String?>>,
    val stopChanges: List<StopTimeChangeDto>,
    val runTimeDeltaSec: Int?,
)

data class StopTimeChangeDto(
    val stopSequence: Int,
    val kind: StopChangeKind,
    val stopId: String?,
    val arrivalDeltaSec: Int?,
    val departureDeltaSec: Int?,
)

data class CalendarChangeDto(
    val serviceId: String,
    val kind: CalendarChangeKind,
    val fieldChanges: Map<String, Map<String, String?>>,
    val exceptionChanges: List<CalendarExceptionChangeDto>,
)

data class CalendarExceptionChangeDto(
    val date: String,
    val kind: ExceptionChangeKind,
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
