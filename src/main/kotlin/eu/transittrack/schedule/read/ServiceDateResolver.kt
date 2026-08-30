package eu.transittrack.schedule.read

import eu.transittrack.gtfs.model.GtfsCalendar
import eu.transittrack.gtfs.model.GtfsCalendarDateRepository
import eu.transittrack.gtfs.model.GtfsCalendarRepository
import java.time.DayOfWeek
import java.time.LocalDate
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Component

/**
 * Resolves which GTFS `service_id`s run on a given calendar date for a revision
 * (calendar weekday windows + calendar_dates exceptions). All results are in
 * service-day terms; no timezone handling.
 */
@Component
class ServiceDateResolver(
    private val calendars: GtfsCalendarRepository,
    private val calendarDates: GtfsCalendarDateRepository,
) {
    @Cacheable("activeServiceIds")
    fun activeServiceIds(revisionId: Long, date: LocalDate): Set<String> {
        val active = calendars.findByRevisionId(revisionId)
            .filter { withinWindow(it, date) && runsOnWeekday(it, date.dayOfWeek) }
            .map { it.serviceId }
            .toMutableSet()
        for (cd in calendarDates.findByRevisionId(revisionId)) {
            if (cd.date != date) continue
            when (cd.exceptionType) {
                1 -> active.add(cd.serviceId)
                2 -> active.remove(cd.serviceId)
            }
        }
        return active
    }

    private fun withinWindow(c: GtfsCalendar, date: LocalDate): Boolean {
        val start = c.startDate
        val end = c.endDate
        return (start == null || !date.isBefore(start)) && (end == null || !date.isAfter(end))
    }

    private fun runsOnWeekday(c: GtfsCalendar, dow: DayOfWeek): Boolean = when (dow) {
        DayOfWeek.MONDAY -> c.monday
        DayOfWeek.TUESDAY -> c.tuesday
        DayOfWeek.WEDNESDAY -> c.wednesday
        DayOfWeek.THURSDAY -> c.thursday
        DayOfWeek.FRIDAY -> c.friday
        DayOfWeek.SATURDAY -> c.saturday
        DayOfWeek.SUNDAY -> c.sunday
    } == true
}
