package eu.transittrack.schedule.compare

import java.time.LocalDate

enum class TripChangeKind { ADDED, REMOVED, MODIFIED }

enum class StopChangeKind { STOP_ADDED, STOP_REMOVED, STOP_TIME_CHANGED }

enum class CalendarChangeKind { ADDED, REMOVED, MODIFIED }

enum class ExceptionChangeKind { EXCEPTION_ADDED, EXCEPTION_REMOVED, EXCEPTION_CHANGED }

data class StopTimeChange(
    val stopSequence: Int,
    val kind: StopChangeKind,
    val stopId: String?,
    val arrivalDeltaSec: Int?,
    val departureDeltaSec: Int?,
)

data class TripChange(
    val tripId: String,
    val kind: TripChangeKind,
    val fieldChanges: Map<String, Pair<String?, String?>>,
    val stopChanges: List<StopTimeChange>,
    val runTimeDeltaSec: Int?,
)

data class CalendarExceptionChange(
    val date: LocalDate,
    val kind: ExceptionChangeKind,
    val fromType: Int?,
    val toType: Int?,
)

data class CalendarChange(
    val serviceId: String,
    val kind: CalendarChangeKind,
    val fieldChanges: Map<String, Pair<String?, String?>>,
    val exceptionChanges: List<CalendarExceptionChange>,
)

data class HeadwaySummary(
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

data class ScheduleComparison(
    val fromRevisionId: Long,
    val toRevisionId: Long,
    val fromDerivationStale: Boolean,
    val toDerivationStale: Boolean,
    val tripChanges: List<TripChange>,
    val calendarChanges: List<CalendarChange>,
    val headwaySummaries: List<HeadwaySummary>,
)
