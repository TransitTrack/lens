package eu.transittrack.avl.match

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

import org.springframework.stereotype.Component

data class SchedulePoint(
    val stopPathIndex: Int,
    val arrivalSec: Int?,
    val departureSec: Int?,
)

/**
 * Schedule adherence: interpolate the scheduled service-day clock at the vehicle's along-trip
 * distance from the trip's ordered [SchedulePoint]s and the per-pattern cumulative stop-path
 * distances, then subtract from the actual service-day seconds. Negative = early.
 */
@Component
class TemporalMatcher {
    private companion object {
        const val MAX_ABS_ADHERENCE_SEC = 7_200
    }

    fun adherenceSec(
        schedule: List<SchedulePoint>,
        stopPathCumM: DoubleArray,
        distanceAlongTripM: Double,
        actualServiceSec: Int,
        noSchedule: Boolean,
    ): Int? {
        if (noSchedule || schedule.size < 2) return null
        val n = minOf(schedule.size, stopPathCumM.size)
        if (n < 2) return null

        var i = 0
        while (i < n - 2 && stopPathCumM[i + 1] <= distanceAlongTripM) i++
        val segFrom = stopPathCumM[i]
        val segTo = stopPathCumM[i + 1]
        val frac =
            if (segTo <= segFrom) {
                0.0
            } else {
                ((distanceAlongTripM - segFrom) / (segTo - segFrom)).coerceIn(0.0, 1.0)
            }

        val from = (schedule[i].departureSec ?: schedule[i].arrivalSec) ?: return null
        val to = (schedule[i + 1].arrivalSec ?: schedule[i + 1].departureSec) ?: return null
        val scheduledSec = from + frac * (to - from)

        val adherence = (actualServiceSec - scheduledSec).toInt()
        return if (kotlin.math.abs(adherence) > MAX_ABS_ADHERENCE_SEC) null else adherence
    }

    fun toServiceSeconds(
        ts: Instant,
        zone: ZoneId,
        serviceDate: LocalDate,
    ): Int {
        val midnight = serviceDate.atStartOfDay(zone).toInstant()
        return Duration.between(midnight, ts).seconds.toInt()
    }
}
