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
    private val cum = doubleArrayOf(0.0, 1000.0, 2000.0)
    private val sched = listOf(
        SchedulePoint(0, arrivalSec = 36000, departureSec = 36000), // 10:00:00
        SchedulePoint(1, arrivalSec = 36120, departureSec = 36150), // 10:02:00 / 10:02:30
        SchedulePoint(2, arrivalSec = 36300, departureSec = 36300), // 10:05:00
    )

    @Test
    fun `interpolates scheduled clock at a mid-path distance`() {
        // halfway between path 0 (dep 36000) and path 1 (arr 36120) → 36060
        // actual 36090 → +30 s late
        assertThat(tm.adherenceSec(sched, cum, 500.0, 36090, noSchedule = false)).isEqualTo(30)
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
