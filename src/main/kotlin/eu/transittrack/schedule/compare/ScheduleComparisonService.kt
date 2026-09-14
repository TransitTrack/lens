package eu.transittrack.schedule.compare

import org.springframework.stereotype.Service

import eu.transittrack.gtfs.model.Calendar
import eu.transittrack.gtfs.model.CalendarDateRepository
import eu.transittrack.gtfs.model.CalendarRepository
import eu.transittrack.gtfs.model.StopTimeRepository
import eu.transittrack.gtfs.model.Trip
import eu.transittrack.gtfs.model.TripRepository
import eu.transittrack.gtfs.revision.GtfsRevisionRepository

@Service
class ScheduleComparisonService(
    private val revisions: GtfsRevisionRepository,
    private val trips: TripRepository,
    private val stopTimes: StopTimeRepository,
    private val calendars: CalendarRepository,
    private val calendarDates: CalendarDateRepository,
) {
    fun compare(
        fromRevisionId: Long,
        toRevisionId: Long,
        routeId: String? = null,
        directionId: Int? = null,
        serviceId: String? = null,
    ): ScheduleComparison {
        val from = revisions.findById(fromRevisionId).orElseThrow { IllegalArgumentException("no revision $fromRevisionId") }
        val to = revisions.findById(toRevisionId).orElseThrow { IllegalArgumentException("no revision $toRevisionId") }
        require(from.feedId == to.feedId) {
            "revisions $fromRevisionId and $toRevisionId belong to different feeds"
        }

        val fromTrips = filterTrips(trips.findByRevisionId(fromRevisionId), routeId, directionId, serviceId)
        val toTrips = filterTrips(trips.findByRevisionId(toRevisionId), routeId, directionId, serviceId)
        val tripChanges =
            compareTrips(fromRevisionId, toRevisionId, fromTrips, toTrips, from.derivationStale, to.derivationStale)

        val calendarServiceIds: Set<String>? =
            when {
                serviceId != null -> {
                    setOf(serviceId)
                }

                routeId != null || directionId != null -> {
                    (fromTrips.map { it.serviceId } + toTrips.map { it.serviceId }).toSet()
                }

                else -> {
                    null
                }
            }
        val calendarChanges = compareCalendars(fromRevisionId, toRevisionId, calendarServiceIds)

        return ScheduleComparison(
            fromRevisionId = fromRevisionId,
            toRevisionId = toRevisionId,
            fromDerivationStale = from.derivationStale,
            toDerivationStale = to.derivationStale,
            tripChanges = tripChanges,
            calendarChanges = calendarChanges,
            headwaySummaries = emptyList(),
        )
    }

    private fun filterTrips(
        all: List<Trip>,
        routeId: String?,
        directionId: Int?,
        serviceId: String?,
    ): List<Trip> =
        all.filter { t ->
            (routeId == null || t.routeId == routeId) &&
                (directionId == null || t.directionId == directionId) &&
                (serviceId == null || t.serviceId == serviceId)
        }

    private fun compareTrips(
        fromRevisionId: Long,
        toRevisionId: Long,
        fromTrips: List<Trip>,
        toTrips: List<Trip>,
        fromStale: Boolean,
        toStale: Boolean,
    ): List<TripChange> {
        val fromById = fromTrips.associateBy { it.tripId }
        val toById = toTrips.associateBy { it.tripId }
        val allIds = fromById.keys + toById.keys
        val changes = mutableListOf<TripChange>()
        for (tripId in allIds) {
            val f = fromById[tripId]
            val t = toById[tripId]
            when {
                f == null && t != null -> {
                    changes += TripChange(tripId, TripChangeKind.ADDED, emptyMap(), emptyList(), null)
                }

                f != null && t == null -> {
                    changes += TripChange(tripId, TripChangeKind.REMOVED, emptyMap(), emptyList(), null)
                }

                f != null && t != null -> {
                    val fieldChanges = tripFieldChanges(f, t)
                    val stopChanges = compareStopTimes(fromRevisionId, toRevisionId, tripId)
                    if (fieldChanges.isEmpty() && stopChanges.isEmpty()) continue
                    val runTimeDelta =
                        if (!fromStale &&
                            !toStale &&
                            f.startTimeSec != null &&
                            f.endTimeSec != null &&
                            t.startTimeSec != null &&
                            t.endTimeSec != null
                        ) {
                            (t.endTimeSec!! - t.startTimeSec!!) - (f.endTimeSec!! - f.startTimeSec!!)
                        } else {
                            null
                        }
                    changes += TripChange(tripId, TripChangeKind.MODIFIED, fieldChanges, stopChanges, runTimeDelta)
                }
            }
        }
        return changes.sortedBy { it.tripId }
    }

    private fun tripFieldChanges(
        f: Trip,
        t: Trip,
    ): Map<String, Pair<String?, String?>> {
        val m = mutableMapOf<String, Pair<String?, String?>>()
        if (f.routeId != t.routeId) m["routeId"] = f.routeId to t.routeId
        if (f.serviceId != t.serviceId) m["serviceId"] = f.serviceId to t.serviceId
        if (f.directionId != t.directionId) m["directionId"] = f.directionId?.toString() to t.directionId?.toString()
        if (f.blockId != t.blockId) m["blockId"] = f.blockId to t.blockId
        if (f.shapeId != t.shapeId) m["shapeId"] = f.shapeId to t.shapeId
        if (f.tripHeadsign != t.tripHeadsign) m["headsign"] = f.tripHeadsign to t.tripHeadsign
        return m
    }

    private fun compareStopTimes(
        fromRevisionId: Long,
        toRevisionId: Long,
        tripId: String,
    ): List<StopTimeChange> {
        val fromRows = stopTimes.findByTripId(fromRevisionId, tripId).associateBy { it.stopSequence }
        val toRows = stopTimes.findByTripId(toRevisionId, tripId).associateBy { it.stopSequence }
        val allSeqs = (fromRows.keys + toRows.keys).sorted()
        val out = mutableListOf<StopTimeChange>()
        for (seq in allSeqs) {
            val f = fromRows[seq]
            val t = toRows[seq]
            when {
                f == null && t != null -> {
                    out += StopTimeChange(seq, StopChangeKind.STOP_ADDED, t.stopId, null, null)
                }

                f != null && t == null -> {
                    out += StopTimeChange(seq, StopChangeKind.STOP_REMOVED, f.stopId, null, null)
                }

                f != null && t != null -> {
                    val stopChanged = f.stopId != t.stopId
                    val arrDelta = if (f.arrivalTime != null && t.arrivalTime != null) t.arrivalTime!! - f.arrivalTime!! else null
                    val depDelta = if (f.departureTime != null && t.departureTime != null) t.departureTime!! - f.departureTime!! else null
                    val arrNullChanged = (f.arrivalTime == null) != (t.arrivalTime == null)
                    val depNullChanged = (f.departureTime == null) != (t.departureTime == null)
                    val timeChanged =
                        (arrDelta != null && arrDelta != 0) ||
                            (depDelta != null && depDelta != 0) ||
                            arrNullChanged ||
                            depNullChanged
                    if (stopChanged || timeChanged) {
                        out += StopTimeChange(seq, StopChangeKind.STOP_TIME_CHANGED, t.stopId, arrDelta, depDelta)
                    }
                }
            }
        }
        return out
    }

    private fun compareCalendars(
        fromRevisionId: Long,
        toRevisionId: Long,
        serviceIds: Set<String>?,
    ): List<CalendarChange> {
        val fromCals = loadCalendars(fromRevisionId, serviceIds).associateBy { it.serviceId }
        val toCals = loadCalendars(toRevisionId, serviceIds).associateBy { it.serviceId }
        val allServiceIds = (fromCals.keys + toCals.keys).sorted()
        val out = mutableListOf<CalendarChange>()
        for (svc in allServiceIds) {
            val f = fromCals[svc]
            val t = toCals[svc]
            val exceptionChanges = compareExceptions(fromRevisionId, toRevisionId, svc)
            when {
                f == null && t != null -> {
                    out += CalendarChange(svc, CalendarChangeKind.ADDED, emptyMap(), exceptionChanges)
                }

                f != null && t == null -> {
                    out += CalendarChange(svc, CalendarChangeKind.REMOVED, emptyMap(), exceptionChanges)
                }

                f != null && t != null -> {
                    val fields = calendarFieldChanges(f, t)
                    if (fields.isNotEmpty() || exceptionChanges.isNotEmpty()) {
                        out += CalendarChange(svc, CalendarChangeKind.MODIFIED, fields, exceptionChanges)
                    }
                }
            }
        }
        return out
    }

    private fun loadCalendars(
        revisionId: Long,
        serviceIds: Set<String>?,
    ): List<Calendar> =
        if (serviceIds == null) {
            calendars.findByRevisionId(revisionId)
        } else {
            serviceIds.mapNotNull { calendars.findByServiceId(revisionId, it) }
        }

    private fun calendarFieldChanges(
        f: Calendar,
        t: Calendar,
    ): Map<String, Pair<String?, String?>> {
        val m = mutableMapOf<String, Pair<String?, String?>>()

        fun cmp(
            name: String,
            fv: Any?,
            tv: Any?,
        ) {
            if (fv != tv) m[name] = fv?.toString() to tv?.toString()
        }
        cmp("monday", f.monday, t.monday)
        cmp("tuesday", f.tuesday, t.tuesday)
        cmp("wednesday", f.wednesday, t.wednesday)
        cmp("thursday", f.thursday, t.thursday)
        cmp("friday", f.friday, t.friday)
        cmp("saturday", f.saturday, t.saturday)
        cmp("sunday", f.sunday, t.sunday)
        cmp("startDate", f.startDate, t.startDate)
        cmp("endDate", f.endDate, t.endDate)
        return m
    }

    private fun compareExceptions(
        fromRevisionId: Long,
        toRevisionId: Long,
        serviceId: String,
    ): List<CalendarExceptionChange> {
        val fromEx = calendarDates.findByServiceId(fromRevisionId, serviceId).associateBy { it.date }
        val toEx = calendarDates.findByServiceId(toRevisionId, serviceId).associateBy { it.date }
        val allDates = (fromEx.keys + toEx.keys).sorted()
        val out = mutableListOf<CalendarExceptionChange>()
        for (date in allDates) {
            val f = fromEx[date]
            val t = toEx[date]
            when {
                f == null && t != null -> {
                    out += CalendarExceptionChange(date, ExceptionChangeKind.EXCEPTION_ADDED, null, t.exceptionType)
                }

                f != null && t == null -> {
                    out += CalendarExceptionChange(date, ExceptionChangeKind.EXCEPTION_REMOVED, f.exceptionType, null)
                }

                f != null && t != null && f.exceptionType != t.exceptionType -> {
                    out += CalendarExceptionChange(date, ExceptionChangeKind.EXCEPTION_CHANGED, f.exceptionType, t.exceptionType)
                }
            }
        }
        return out
    }
}
