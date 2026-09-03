package eu.transittrack.schedule.derive

import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue

class ScheduleInterpolatorTest {
    @Test
    fun `fills a blank middle stop by distance`() {
        val raw =
            listOf(
                RawStopTime(0, 32400, 32400), // 09:00:00
                RawStopTime(1, null, null), // blank
                RawStopTime(2, 33600, 33600), // 09:20:00
                RawStopTime(3, 34200, 34200), // 09:30:00
            )
        // S2 is 1/3 of the distance from S1 to S3
        val dist = doubleArrayOf(0.0, 300.0, 900.0, 1500.0)
        val out = ScheduleInterpolator.resolve(raw, dist)
        assertThat(out).hasSize(4)
        assertThat(out[1].interpolated).isTrue()
        // 32400 + (33600-32400) * (300/900) = 32800
        assertThat(out[1].arrivalSec).isEqualTo(32800)
        assertThat(out[1].departureSec).isEqualTo(32800)
        assertThat(out[0].interpolated).isFalse()
    }

    @Test
    fun `travel and dwell are computed`() {
        val raw =
            listOf(
                RawStopTime(0, 100, 130),
                RawStopTime(1, 200, 210),
            )
        val out = ScheduleInterpolator.resolve(raw, doubleArrayOf(0.0, 500.0))
        assertThat(out[0].schedTravelTimeSec).isNull()
        assertThat(out[0].schedDwellTimeSec).isEqualTo(30)
        assertThat(out[1].schedTravelTimeSec).isEqualTo(70) // 200 - 130
        assertThat(out[1].schedDwellTimeSec).isEqualTo(10)
    }

    @Test
    fun `single known time populates both arr and dep`() {
        val raw =
            listOf(
                RawStopTime(0, null, 100),
                RawStopTime(1, 200, null),
            )
        val out = ScheduleInterpolator.resolve(raw, doubleArrayOf(0.0, 100.0))
        assertThat(out[0].arrivalSec).isEqualTo(100)
        assertThat(out[1].departureSec).isEqualTo(200)
    }

    @Test
    fun `zero-distance span interpolates evenly by index`() {
        val raw =
            listOf(
                RawStopTime(0, 0, 0),
                RawStopTime(1, null, null),
                RawStopTime(2, null, null),
                RawStopTime(3, 300, 300),
            )
        val out = ScheduleInterpolator.resolve(raw, doubleArrayOf(0.0, 0.0, 0.0, 0.0))
        assertThat(out[1].arrivalSec).isEqualTo(100)
        assertThat(out[2].arrivalSec).isEqualTo(200)
    }
}
