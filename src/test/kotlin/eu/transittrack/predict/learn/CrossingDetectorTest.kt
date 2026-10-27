package eu.transittrack.predict.learn

import java.time.Instant
import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo

class CrossingDetectorTest {
    // Production-shaped pattern: stop path 0 is zero-length (ends at the first stop, distance 0),
    // stop 1 at 1000m, stop 2 at 2500m, stop 3 (last) at the pattern's end, 3000m.
    private val cum = doubleArrayOf(0.0, 0.0, 1000.0, 2500.0)
    private val len = 3000.0
    private val t0 = Instant.parse("2026-09-07T08:00:00Z")

    private fun passes(
        prevAlong: Double,
        along: Double,
        elapsedSec: Long,
    ) = detectStopPasses(prevAlong, t0, along, t0.plusSeconds(elapsedSec), cum, len)

    @Test
    fun `reaching a stop records that stop - the one stop path k ends at - not the next one`() {
        // 500m -> 1500m in 100s crosses stop 1 (at 1000m) halfway through the interval.
        assertThat(passes(500.0, 1500.0, 100)).containsExactly(StopPass(1, t0.plusSeconds(50)))
    }

    @Test
    fun `several stops between two reports each get their own interpolated pass time`() {
        // 500m -> 2500m in 200s: stop 1 at 1000m (fraction 0.25), stop 2 at 2500m (fraction 1.0).
        assertThat(passes(500.0, 2500.0, 200))
            .containsExactly(StopPass(1, t0.plusSeconds(50)), StopPass(2, t0.plusSeconds(200)))
    }

    @Test
    fun `moving within one stop path passes nothing`() {
        assertThat(passes(1100.0, 1900.0, 60)).isEmpty()
    }

    @Test
    fun `leaving the first stop is a pass at the interval midpoint`() {
        // Standing at the terminus (5m - GPS noise), then 400m along: departed stop 0 at some point.
        assertThat(passes(5.0, 400.0, 60)).containsExactly(StopPass(0, t0.plusSeconds(30)))
    }

    @Test
    fun `already past the first stop's radius does not record a departure`() {
        assertThat(passes(100.0, 400.0, 60)).isEmpty()
    }

    @Test
    fun `last stop is reached within its radius of the pattern's end`() {
        // 2800m -> 3000m in 40s reaches 2970m (3000 - LAST_STOP_RADIUS_M) 34s in.
        assertThat(passes(2800.0, 3000.0, 40)).containsExactly(StopPass(3, t0.plusSeconds(34)))
        // A vehicle pulling up short of where the shape ends still reaches it.
        assertThat(passes(2800.0, 2980.0, 40)).containsExactly(StopPass(3, t0.plusSeconds(37) + java.time.Duration.ofMillis(777)))
        assertThat(passes(2800.0, 2960.0, 40)).isEmpty()
    }

    @Test
    fun `a short last stop path is reached no earlier than its midpoint`() {
        // Last stop path 2990m -> 3000m: reached at 2995m, not at 2970m before the previous stop.
        val shortCum = doubleArrayOf(0.0, 0.0, 1000.0, 2990.0)
        assertThat(stopReachedAtM(3, shortCum, len)).isEqualTo(2995.0)
        assertThat(detectStopPasses(2980.0, t0, 2992.0, t0.plusSeconds(10), shortCum, len))
            .containsExactly(StopPass(2, t0.plusSeconds(8) + java.time.Duration.ofMillis(333)))
    }

    @Test
    fun `no forward movement or non-positive elapsed time passes nothing`() {
        assertThat(passes(1500.0, 500.0, 60)).isEmpty()
        assertThat(passes(500.0, 1500.0, 0)).isEmpty()
        assertThat(passes(500.0, 1500.0, -5)).isEmpty()
    }
}
