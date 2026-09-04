package eu.transittrack.predict.learn

import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo

class CrossingDetectorTest {
    // 3 stop paths: cum = [0, 1000, 2500], pattern length 3000 -> lengths 1000, 1500, 500
    private val cum = doubleArrayOf(0.0, 1000.0, 2500.0)
    private val len = 3000.0

    @Test
    fun `single-stop advance apportions all elapsed time to it`() {
        val c = detectCrossings(0, 1, elapsedSec = 60.0, cum, len, maxPlausibleSec = 1800)
        assertThat(c).hasSize(1)
        assertThat(c[0].stopPathIndex).isEqualTo(1)
        assertThat(c[0].observedTravelTimeSec).isEqualTo(60.0)
    }

    @Test
    fun `multi-stop skip apportions by length share`() {
        // crossing 1 and 2: lengths 1500 and 500, total 2000; elapsed 100s
        val c = detectCrossings(0, 2, elapsedSec = 100.0, cum, len, maxPlausibleSec = 1800)
        assertThat(c).hasSize(2)
        assertThat(c[0].observedTravelTimeSec).isEqualTo(75.0) // 1500/2000 * 100
        assertThat(c[1].observedTravelTimeSec).isEqualTo(25.0) // 500/2000 * 100
    }

    @Test
    fun `no advance returns empty`() {
        assertThat(detectCrossings(1, 1, 60.0, cum, len, 1800)).isEmpty()
        assertThat(detectCrossings(2, 1, 60.0, cum, len, 1800)).isEmpty()
    }

    @Test
    fun `implausible sample is dropped, plausible siblings kept`() {
        // crossing 1 and 2 with a huge elapsed time -> both shares exceed maxPlausibleSec except tune one to survive
        val c = detectCrossings(0, 2, elapsedSec = 4000.0, cum, len, maxPlausibleSec = 1800)
        // 1500/2000*4000=3000 (dropped), 500/2000*4000=1000 (kept)
        assertThat(c).hasSize(1)
        assertThat(c[0].stopPathIndex).isEqualTo(2)
        assertThat(c[0].observedTravelTimeSec).isEqualTo(1000.0)
    }

    @Test
    fun `zero or negative elapsed produces no plausible samples`() {
        assertThat(detectCrossings(0, 1, elapsedSec = 0.0, cum, len, 1800)).isEmpty()
        assertThat(detectCrossings(0, 1, elapsedSec = -5.0, cum, len, 1800)).isEmpty()
    }
}
