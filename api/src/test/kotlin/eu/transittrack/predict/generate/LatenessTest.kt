package eu.transittrack.predict.generate

import assertk.assertThat
import assertk.assertions.isBetween
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isLessThan
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import java.time.Instant
import kotlin.test.Test

class LatenessTest {
    private val policy = LatenessPolicy(scheduleToleranceSec = 300, headwayFraction = 0.5)
    private val scheduled = Instant.parse("2026-01-01T10:00:00Z")

    private fun assess(
        delaySec: Long,
        sigma: Double,
        headway: Int? = null,
    ) = assessLateness(policy, scheduled, scheduled.plusSeconds(delaySec), sigma, headway)

    @Test
    fun lateWhenDelayExceedsTolerance() {
        val result = assess(420, 120.0)
        assertThat(result).isNotNull()
        assertThat(result!!.late).isTrue()
        assertThat(result.probability).isBetween(0.831, 0.851)
    }

    @Test
    fun onTimeWhenWellInsideTolerance() {
        val result = assess(0, 60.0)
        assertThat(result!!.late).isFalse()
        assertThat(result.probability).isLessThan(0.001)
    }

    @Test
    fun zeroSigmaIsAStep() {
        assertThat(assess(301, 0.0)).isEqualTo(LateAssessment(true, 1.0))
        assertThat(assess(300, 0.0)).isEqualTo(LateAssessment(false, 0.0))
    }

    @Test
    fun frequentRouteUsesHeadwayFraction() {
        assertThat(assess(240, 60.0, headway = 600)!!.late).isFalse()
        assertThat(assess(360, 60.0, headway = 600)!!.late).isTrue()
    }

    @Test
    fun noScheduleAndNoHeadwayIsNull() {
        assertThat(assessLateness(policy, null, scheduled, 60.0, null)).isNull()
    }

    @Test
    fun headwayWithoutScheduledArrivalIsNull() {
        assertThat(assessLateness(policy, null, scheduled, 60.0, 600)).isNull()
    }

    @Test
    fun earlyIsNeverLate() {
        assertThat(assess(-200, 60.0)!!.late).isFalse()
    }
}
