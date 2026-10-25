package eu.transittrack.predict.learn

import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.isBetween
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan

class TravelTimeLearnersTest {
    @Test
    fun `running average seeds from schedule on first sample`() {
        val s = updateRunningAverage(prev = null, seed = 100.0, sample = 120.0, window = 50)
        assertThat(s.sampleCount).isEqualTo(1L)
        assertThat(s.meanSec).isEqualTo(120.0)
    }

    @Test
    fun `running average is a plain mean within its window`() {
        var s = updateRunningAverage(null, seed = 100.0, sample = 110.0, window = 50)
        s = updateRunningAverage(s, seed = 100.0, sample = 130.0, window = 50)
        s = updateRunningAverage(s, seed = 100.0, sample = 120.0, window = 50)
        assertThat(s.sampleCount).isEqualTo(3L)
        assertThat(s.meanSec).isEqualTo(120.0)
    }

    @Test
    fun `running average keeps adapting once past its window`() {
        var s: RunningAverageState? = null
        repeat(1_000) { s = updateRunningAverage(s, seed = 100.0, sample = 100.0, window = 10) }
        repeat(30) { s = updateRunningAverage(s, seed = 100.0, sample = 200.0, window = 10) }
        // A cumulative mean would still be ~103 after 1000 samples of 100; a 10-sample window
        // has moved almost all the way to the new level.
        assertThat(s!!.meanSec).isGreaterThan(190.0)
    }

    @Test
    fun `kalman seeds from schedule with initial variance on its first sample`() {
        val s =
            updateKalman(prev = null, seed = 100.0, initialVariance = 400.0, measurementNoise = 100.0, processNoise = 25.0, sample = 200.0)
        // no process noise on the very first update: gain = 400/500 = 0.8
        assertThat(s.estimateSec).isEqualTo(180.0)
        assertThat(s.errorVariance).isEqualTo(80.0)
        assertThat(s.sampleCount).isEqualTo(1L)
    }

    @Test
    fun `kalman adds process noise before every later update`() {
        val first = updateKalman(null, seed = 100.0, initialVariance = 400.0, measurementNoise = 100.0, processNoise = 20.0, sample = 200.0)
        val second =
            updateKalman(first, seed = 100.0, initialVariance = 400.0, measurementNoise = 100.0, processNoise = 20.0, sample = 200.0)
        // prior = 80 + 20 = 100 -> gain 0.5 -> 180 + 0.5 * 20 = 190
        assertThat(second.estimateSec).isEqualTo(190.0)
        assertThat(second.errorVariance).isEqualTo(50.0)
    }

    @Test
    fun `kalman variance settles at a floor instead of decaying to zero`() {
        var s: KalmanState? = null
        repeat(500) {
            s =
                updateKalman(s, seed = 100.0, initialVariance = 400.0, measurementNoise = 400.0, processNoise = 25.0, sample = 150.0)
        }
        // steady state for Q=25, R=400: prior x solves x^2 - Qx - QR = 0 -> x ~= 113, posterior ~= 88
        assertThat(s!!.errorVariance).isBetween(80.0, 95.0)
    }
}
