package eu.transittrack.avl.match

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull

class TemporalMatcherTest {
    private val tm = TemporalMatcher()

    // Mirrors production pattern geometry: stop_path 0 always has zero length - it's the degenerate
    // path ending at the trip's own first stop (see PatternBuild.kt's `i == 0 -> segLen = 0.0`), so
    // stopPathCumM[0] == stopPathCumM[1]. Path 1 then runs from stop 0 to stop 1 (1000m), path 2
    // from stop 1 onward.
    private val cum = doubleArrayOf(0.0, 0.0, 1000.0)
    private val sched = listOf(
        SchedulePoint(0, arrivalSec = 36000, departureSec = 36000), // 10:00:00 - stop 0 (trip start)
        SchedulePoint(1, arrivalSec = 36120, departureSec = 36150), // 10:02:00 / 10:02:30 - stop 1
        SchedulePoint(2, arrivalSec = 36300, departureSec = 36300), // 10:05:00 - stop 2
    )

    @Test
    fun `interpolates scheduled clock at a mid-path distance`() {
        // halfway along path 1 (stop 0 -> stop 1): dep 36000 (stop 0) to arr 36120 (stop 1) → 36060
        // actual 36090 → +30 s late
        assertThat(tm.adherenceSec(sched, cum, 500.0, 36090, noSchedule = false)).isEqualTo(30)
    }

    @Test
    fun `a vehicle waiting at the first stop before trip start is early, not late`() {
        // distanceAlongTripM = 0, the degenerate path-0 segment - scheduledSec must resolve to
        // schedule[0]'s own time (36000, the trip start), not a time borrowed from the NEXT stop's
        // SchedulePoint. 5 minutes early (35700) must read -300s, never positive/"late".
        assertThat(tm.adherenceSec(sched, cum, 0.0, 35700, noSchedule = false)).isEqualTo(-300)
    }

    @Test
    fun `no schedule returns null`() {
        assertThat(tm.adherenceSec(sched, cum, 500.0, 36090, noSchedule = true)).isNull()
    }

    @Test
    fun `absurd adherence is nulled`() {
        assertThat(tm.adherenceSec(sched, cum, 500.0, 50000, noSchedule = false)).isNull()
    }

    @Test
    fun `service seconds handle past-midnight`() {
        val z = ZoneId.of("Europe/Bucharest")
        // 01:00 local on 2026-09-05, service date 2026-09-04 → 25:00:00 = 90000
        val ts = LocalDate
            .of(2026, 9, 5)
            .atTime(1, 0)
            .atZone(z)
            .toInstant()
        assertThat(tm.toServiceSeconds(ts, z, LocalDate.of(2026, 9, 4))).isEqualTo(90000)
    }
}
