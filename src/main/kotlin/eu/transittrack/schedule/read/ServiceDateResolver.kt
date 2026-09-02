package eu.transittrack.schedule.read

import java.time.DayOfWeek
import java.time.LocalDate

import org.springframework.stereotype.Component

import eu.transittrack.gtfs.model.Calendar
import eu.transittrack.gtfs.model.CalendarDateRepository
import eu.transittrack.gtfs.model.CalendarRepository

/**
 * Resolves which GTFS `service_id`s run on a given calendar date for a revision (calendar weekday
 * windows + calendar_dates exceptions). All results are in service-day terms; no timezone handling.
 *
 * Not cached: the date comes from the API client, so any cache would be keyed on unbounded input.
 * Each call is two small indexed reads.
 */
@Component
class ServiceDateResolver(
    private val calendars: CalendarRepository,
    private val calendarDates: CalendarDateRepository,
) {
    fun activeServiceIds(
        revisionId: Long,
        date: LocalDate,
    ): Set<String> {
        val active =
            calendars
                .findByRevisionId(revisionId)
                .filter {
                    withinWindow(it, date) && runsOnWeekday(it, date.dayOfWeek)
                }.map { it.serviceId }
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

    private fun withinWindow(
        c: Calendar,
        date: LocalDate,
    ): Boolean {
        val start = c.startDate
        val end = c.endDate
        return (start == null || !date.isBefore(start)) && (end == null || !date.isAfter(end))
    }

    private fun runsOnWeekday(
        c: Calendar,
        dow: DayOfWeek,
    ): Boolean =
        when (dow) {
            DayOfWeek.MONDAY -> c.monday
            DayOfWeek.TUESDAY -> c.tuesday
            DayOfWeek.WEDNESDAY -> c.wednesday
            DayOfWeek.THURSDAY -> c.thursday
            DayOfWeek.FRIDAY -> c.friday
            DayOfWeek.SATURDAY -> c.saturday
            DayOfWeek.SUNDAY -> c.sunday
        } == true
}
