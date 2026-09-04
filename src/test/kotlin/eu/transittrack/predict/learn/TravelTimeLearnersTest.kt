package eu.transittrack.predict.learn

import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.isEqualTo

class TravelTimeLearnersTest {
    @Test
    fun `running average seeds from schedule on first sample`() {
        val s = updateRunningAverage(prev = null, seed = 100.0, sample = 120.0)
        // base mean starts at seed (100), first update moves it toward 120 by 1/1
        assertThat(s.sampleCount).isEqualTo(1L)
        assertThat(s.meanSec).isEqualTo(120.0)
    }

    @Test
    fun `running average converges over several samples`() {
        var s = updateRunningAverage(null, seed = 100.0, sample = 110.0)
        s = updateRunningAverage(s, seed = 100.0, sample = 130.0)
        s = updateRunningAverage(s, seed = 100.0, sample = 120.0)
        // mean of 110, 130, 120 = 120
        assertThat(s.sampleCount).isEqualTo(3L)
        assertThat(s.meanSec).isEqualTo(120.0)
    }

    @Test
    fun `kalman seeds from schedule with initial variance, then converges`() {
        var s = updateKalman(prev = null, seed = 100.0, initialVariance = 400.0, measurementNoise = 100.0, sample = 200.0)
        // gain = 400/(400+100) = 0.8; estimate = 100 + 0.8*(200-100) = 180; variance = 400*0.2 = 80
        assertThat(s.estimateSec).isEqualTo(180.0)
        assertThat(s.errorVariance).isEqualTo(80.0)
        assertThat(s.sampleCount).isEqualTo(1L)

        s = updateKalman(s, seed = 100.0, initialVariance = 400.0, measurementNoise = 100.0, sample = 200.0)
        // gain = 80/(80+100) = 0.4444...; estimate = 180 + 0.4444*(200-180) = 188.888...
        assertThat(s.estimateSec).isEqualTo(180.0 + (80.0 / 180.0) * 20.0)
        assertThat(s.sampleCount).isEqualTo(2L)
    }

    @Test
    fun `kalman error variance shrinks toward zero as samples accumulate`() {
        var s: KalmanState? = null
        repeat(20) { s = updateKalman(s, seed = 100.0, initialVariance = 400.0, measurementNoise = 100.0, sample = 150.0) }
        assertThat(s!!.errorVariance < 10.0).isEqualTo(true)
    }
}
