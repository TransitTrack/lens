package eu.transittrack.schedule.read

import org.springframework.stereotype.Service

import eu.transittrack.gtfs.model.CalendarRepository
import eu.transittrack.gtfs.model.RouteRepository
import eu.transittrack.gtfs.model.StopRepository
import eu.transittrack.gtfs.read.RevisionResolver
import eu.transittrack.rest.generated.model.StopDeparture
import eu.transittrack.rest.generated.model.StopSchedule
import eu.transittrack.rest.generated.model.StopScheduleCall
import eu.transittrack.rest.generated.model.StopServiceDays
import eu.transittrack.schedule.model.ScheduleTimeRepository
import eu.transittrack.schedule.model.StopPathRepository
import eu.transittrack.schedule.model.TripPatternRepository

/**
 * The schedule at one stop in a handful of queries: the trip patterns that call there (with their
 * routes), the departure time of every clock-time trip at the stop, and what kind of day each service
 * runs on. Replaces a client reading every pattern, trip and schedule time of the feed to work this out.
 */
@Service
class StopScheduleReadService(
    private val resolver: RevisionResolver,
    private val stops: StopRepository,
    private val stopPaths: StopPathRepository,
    private val patterns: TripPatternRepository,
    private val routes: RouteRepository,
    private val scheduleTimes: ScheduleTimeRepository,
    private val calendars: CalendarRepository,
) {
    /** `null` when the stop doesn't exist in the revision. */
    fun stopSchedule(
        feedCode: String,
        stopId: String,
        revisionId: String?,
    ): StopSchedule? {
        val rev = resolver.resolve(feedCode, revisionId)
        stops.findByStopId(rev, stopId) ?: return null

        val paths = stopPaths.findByStopId(rev, stopId)
        val patternsById = patterns.findAllById(paths.map { it.tripPatternId }.distinct()).associateBy { it.id }
        val routesById = routes.findByRouteIds(rev, patternsById.values.map { it.routeId }.distinct()).associateBy { it.routeId }

        val calls =
            paths
                .mapNotNull { path ->
                    val pattern = patternsById[path.tripPatternId] ?: return@mapNotNull null
                    val route = routesById[pattern.routeId]
                    val departures =
                        scheduleTimes
                            .findTimedAtStopPath(rev, path.tripPatternId, path.stopPathIndex)
                            .mapNotNull { time -> (time.departureSec ?: time.arrivalSec)?.let { StopDeparture(time.serviceId, it) } }
                            .sortedBy { it.departureSec }
                    StopScheduleCall(
                        patternKey = pattern.patternKey,
                        routeId = pattern.routeId,
                        tripCount = pattern.tripCount,
                        stopPathIndex = path.stopPathIndex,
                        departures = departures,
                        routeShortName = route?.routeShortName,
                        routeColor = route?.routeColor,
                        routeTextColor = route?.routeTextColor,
                        directionId = pattern.directionId,
                        headsign = pattern.headsign,
                    )
                }.sortedWith(compareBy({ it.patternKey }, { it.stopPathIndex }))

        val serviceIds = calls.flatMap { call -> call.departures.map { it.serviceId } }.toSet()
        val services =
            if (serviceIds.isEmpty()) {
                emptyList()
            } else {
                calendars
                    .findByServiceIds(rev, serviceIds)
                    .map { calendar ->
                        StopServiceDays(
                            serviceId = calendar.serviceId,
                            weekday =
                                listOf(calendar.monday, calendar.tuesday, calendar.wednesday, calendar.thursday, calendar.friday)
                                    .any { it == true },
                            saturday = calendar.saturday == true,
                            sunday = calendar.sunday == true,
                        )
                    }.sortedBy { it.serviceId }
            }
        return StopSchedule(calls = calls, services = services)
    }
}
