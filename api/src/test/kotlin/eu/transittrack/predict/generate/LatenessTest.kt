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
    fun frequentRouteMarginIsTighterThanTolerance() {
        // Half of a 240 s headway (120 s) is tighter than the 300 s tolerance.
        assertThat(assess(130, 0.0, headway = 240)!!.late).isTrue()
        assertThat(assess(100, 0.0, headway = 240)!!.late).isFalse()
    }

    @Test
    fun longHeadwayKeepsTolerance() {
        // Half of an hourly headway would be 30 minutes: the 300 s tolerance stays the margin.
        assertThat(assess(301, 0.0, headway = 3600)!!.late).isTrue()
        assertThat(assess(299, 0.0, headway = 3600)!!.late).isFalse()
    }

    @Test
    fun zeroHeadwayIsTreatedAsUnknown() {
        // A zero (or negative) headway is no headway rule: the tolerance applies.
        assertThat(assess(301, 0.0, headway = 0)).isEqualTo(LateAssessment(true, 1.0))
        assertThat(assess(299, 0.0, headway = 0)).isEqualTo(LateAssessment(false, 0.0))
        assertThat(assess(299, 0.0, headway = -60)).isEqualTo(LateAssessment(false, 0.0))
        assertThat(assessLateness(policy, null, scheduled, 60.0, 0)).isNull()
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

    @Test
    fun actualArrivalIsJudgedWithTheAssessmentMargin() {
        for (headway in listOf(null, 0, 240, 3600)) {
            for (delay in listOf(-60L, 119L, 120L, 121L, 299L, 300L, 301L, 900L)) {
                val arrival = scheduled.plusSeconds(delay)
                assertThat(isLateArrival(policy, scheduled, arrival, headway))
                    .isEqualTo(assessLateness(policy, scheduled, arrival, 60.0, headway)!!.late)
            }
        }
        // A 240 s headway tightens the margin to 120 s.
        assertThat(isLateArrival(policy, scheduled, scheduled.plusSeconds(121), 240)).isTrue()
        assertThat(isLateArrival(policy, scheduled, scheduled.plusSeconds(121), null)).isFalse()
    }
}
