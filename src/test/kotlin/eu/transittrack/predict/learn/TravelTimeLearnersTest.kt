package eu.transittrack.predict.learn

import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.isBetween
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan

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

    @Test
    fun `running average weighs the schedule seed as prior samples`() {
        val s = updateRunningAverage(prev = null, seed = 100.0, sample = 160.0, window = 50, priorSamples = 5)
        // (5 * 100 + 160) / 6
        assertThat(s.meanSec).isEqualTo(110.0)
        assertThat(s.sampleCount).isEqualTo(1L)
    }

    @Test
    fun `running average prior never weighs more than the window`() {
        val s = updateRunningAverage(prev = null, seed = 100.0, sample = 160.0, window = 4, priorSamples = 5)
        assertThat(s.meanSec).isEqualTo(115.0)
    }

    @Test
    fun `sample clip bounds the deviation by an absolute floor or a share of the estimate`() {
        val clip = SampleClip(minDeviationSec = 60.0, maxRelativeDeviation = 1.0)
        assertThat(clip.clip(sample = 1_200.0, estimate = 30.0)).isEqualTo(90.0)
        assertThat(clip.clip(sample = 1_200.0, estimate = 120.0)).isEqualTo(240.0)
        assertThat(clip.clip(sample = 0.0, estimate = 120.0)).isEqualTo(0.0)
        assertThat(clip.clip(sample = 150.0, estimate = 120.0)).isEqualTo(150.0)
    }

    @Test
    fun `kalman barely moves on a single outlier once clipped`() {
        val clip = SampleClip(minDeviationSec = 60.0, maxRelativeDeviation = 1.0)
        var s: KalmanState? = null
        repeat(200) { s = updateKalman(s, 120.0, 3_600.0, 400.0, 25.0, sample = 120.0, clip = clip) }
        val unclipped = updateKalman(s, 120.0, 3_600.0, 400.0, 25.0, sample = 1_200.0)
        val clipped = updateKalman(s, 120.0, 3_600.0, 400.0, 25.0, sample = 1_200.0, clip = clip)
        // gain ~0.22: unclipped 120 -> ~358, clipped to 240 -> ~146
        assertThat(unclipped.estimateSec).isGreaterThan(300.0)
        assertThat(clipped.estimateSec).isCloseTo(146.0, 2.0)
    }

    @Test
    fun `running average barely moves on a single outlier once clipped`() {
        val clip = SampleClip(minDeviationSec = 60.0, maxRelativeDeviation = 1.0)
        var s: RunningAverageState? = null
        repeat(100) { s = updateRunningAverage(s, 120.0, sample = 120.0, window = 50, clip = clip) }
        val clipped = updateRunningAverage(s, 120.0, sample = 1_200.0, window = 50, clip = clip)
        assertThat(clipped.meanSec).isCloseTo(122.4, 1e-9)
    }

    @Test
    fun `clipped learners still follow a lasting change`() {
        val clip = SampleClip(minDeviationSec = 60.0, maxRelativeDeviation = 1.0)
        var k: KalmanState? = null
        repeat(200) { k = updateKalman(k, 120.0, 3_600.0, 400.0, 25.0, sample = 120.0, clip = clip) }
        repeat(40) { k = updateKalman(k, 120.0, 3_600.0, 400.0, 25.0, sample = 600.0, clip = clip) }
        assertThat(k!!.estimateSec).isGreaterThan(590.0)
    }

    @Test
    fun `the first sample is never clipped against the schedule seed`() {
        val clip = SampleClip(minDeviationSec = 60.0, maxRelativeDeviation = 1.0)
        val k = updateKalman(null, seed = 60.0, initialVariance = 3_600.0, measurementNoise = 400.0, processNoise = 25.0, 600.0, clip)
        assertThat(k.estimateSec).isGreaterThan(500.0)
        val a = updateRunningAverage(null, seed = 60.0, sample = 600.0, window = 50, clip = clip)
        assertThat(a.meanSec).isEqualTo(600.0)
        assertThat(updateRunningAverage(a, seed = 60.0, sample = 6_000.0, window = 50, clip = clip).meanSec).isLessThan(1_000.0)
    }
}
