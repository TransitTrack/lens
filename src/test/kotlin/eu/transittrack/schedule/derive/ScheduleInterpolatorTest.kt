package eu.transittrack.schedule.derive

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScheduleInterpolatorTest {
    @Test fun `fills a blank middle stop by distance`() {
        val raw = listOf(
            RawStopTime(0, 32400, 32400),   // 09:00:00
            RawStopTime(1, null, null),     // blank
            RawStopTime(2, 33600, 33600),   // 09:20:00
            RawStopTime(3, 34200, 34200),   // 09:30:00
        )
        // S2 is 1/3 of the distance from S1 to S3
        val dist = doubleArrayOf(0.0, 300.0, 900.0, 1500.0)
        val out = ScheduleInterpolator.resolve(raw, dist)
        assertEquals(4, out.size)
        assertTrue(out[1].interpolated)
        // 32400 + (33600-32400) * (300/900) = 32800
        assertEquals(32800, out[1].arrivalSec)
        assertEquals(32800, out[1].departureSec)
        assertFalse(out[0].interpolated)
    }

    @Test fun `travel and dwell are computed`() {
        val raw = listOf(
            RawStopTime(0, 100, 130),
            RawStopTime(1, 200, 210),
        )
        val out = ScheduleInterpolator.resolve(raw, doubleArrayOf(0.0, 500.0))
        assertEquals(null, out[0].schedTravelTimeSec)
        assertEquals(30, out[0].schedDwellTimeSec)
        assertEquals(70, out[1].schedTravelTimeSec)  // 200 - 130
        assertEquals(10, out[1].schedDwellTimeSec)
    }

    @Test fun `single known time populates both arr and dep`() {
        val raw = listOf(
            RawStopTime(0, null, 100),
            RawStopTime(1, 200, null),
        )
        val out = ScheduleInterpolator.resolve(raw, doubleArrayOf(0.0, 100.0))
        assertEquals(100, out[0].arrivalSec)
        assertEquals(200, out[1].departureSec)
    }

    @Test fun `zero-distance span interpolates evenly by index`() {
        val raw = listOf(
            RawStopTime(0, 0, 0),
            RawStopTime(1, null, null),
            RawStopTime(2, null, null),
            RawStopTime(3, 300, 300),
        )
        val out = ScheduleInterpolator.resolve(raw, doubleArrayOf(0.0, 0.0, 0.0, 0.0))
        assertEquals(100, out[1].arrivalSec)
        assertEquals(200, out[2].arrivalSec)
    }
}
