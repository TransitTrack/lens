package eu.transittrack

import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import assertk.assertions.isZero

class MathTest {
    @Test
    fun `sqrd squares its argument`() {
        assertThat(sqrd(3.0)).isEqualTo(9.0)
        assertThat(sqrd(-3.0)).isEqualTo(9.0)
        assertThat(sqrd(0.0)).isEqualTo(0.0)
    }

    @Test
    fun `haversine matches a known distance`() {
        // ~1.11 km per 0.01 deg latitude near the equator/mid-latitudes.
        val d = haversineMeters(51.100, 17.000, 51.110, 17.000)
        assertThat(d).isCloseTo(1112.0, 15.0)
    }

    @Test
    fun `haversine is zero for identical points`() {
        assertThat(haversineMeters(51.1, 17.0, 51.1, 17.0)).isZero()
    }
}
