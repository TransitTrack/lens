package eu.transittrack.predict.generate

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

import eu.transittrack.schedule.model.ServiceStopTime

/** How far either side of a period the schedule is looked at for the headway in force then. */
val SCHEDULED_HEADWAY_MARGIN: Duration = Duration.ofHours(1)

/**
 * Average gap between the trips scheduled at a stop within [from]..[to], from those trips' times
 * there ([stopTimes], clock-time trips only), counting only trips whose service runs that day
 * ([activeServiceIds]). `null` when fewer than two fall in the window.
 *
 * Shared by the `headway` read model and the SMILE_GBM lateness assessment, which differ only in
 * where they read [stopTimes] from (the database, and the match context's cached schedule).
 */
fun averageScheduledHeadwaySec(
    stopTimes: List<ServiceStopTime>,
    from: Instant,
    to: Instant,
    zone: ZoneId,
    activeServiceIds: (LocalDate) -> Set<String>,
): Int? {
    if (stopTimes.size < 2) return null
    // Service-day times run past 24:00, so the day before the window's first date can reach into it.
    val firstDate = from.atZone(zone).toLocalDate().minusDays(1)
    val lastDate = to.atZone(zone).toLocalDate()
    val scheduled =
        generateSequence(firstDate) { it.plusDays(1) }
            .takeWhile { !it.isAfter(lastDate) }
            .flatMap { date ->
                val active = activeServiceIds(date)
                stopTimes
                    .asSequence()
                    .filter { it.serviceId in active }
                    .mapNotNull { st -> (st.arrivalSec ?: st.departureSec)?.let { serviceSecToInstant(date, it, zone) } }
            }.filter { !it.isBefore(from) && !it.isAfter(to) }
            .sorted()
            .toList()
    if (scheduled.size < 2) return null
    return scheduled.zipWithNext { a, b -> Duration.between(a, b).seconds }.average().roundToInt()
}
