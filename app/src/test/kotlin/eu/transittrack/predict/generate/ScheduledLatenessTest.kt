package eu.transittrack.predict.generate

import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.isEqualTo

import eu.transittrack.schedule.model.ServiceStopTime

class ScheduledLatenessTest {
    @Test
    fun cachedRouteDayWeighsItsStopTimesAndStops() {
        val t = ServiceStopTime(100, 120, "WK")
        assertThat(weightOf(emptyMap())).isEqualTo(0)
        assertThat(weightOf(mapOf("A" to listOf(t, t, t), "B" to listOf(t)))).isEqualTo(6)
    }
}
