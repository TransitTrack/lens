package eu.transittrack

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class MathTest {
    @Test
    fun `haversine matches a known distance`() {
        // ~1.11 km per 0.01 deg latitude near the equator/mid-latitudes.
        val d = haversineMeters(51.100, 17.000, 51.110, 17.000)
        assertTrue(abs(d - 1112.0) < 15.0, "was $d")
    }

    @Test
    fun `haversine is zero for identical points`() {
        assertTrue(haversineMeters(51.1, 17.0, 51.1, 17.0) == 0.0)
    }
}
