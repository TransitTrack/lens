package eu.transittrack.predict.smile

import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEqualTo

import eu.transittrack.predict.learn.DayType
import eu.transittrack.predict.learn.TimeBand

class StopToStopFeaturesTest {
    @Test
    fun `vector has one value per name`() {
        val v = StopToStopFeatures.vector(3, 42.0, DayType.WEEKDAY, TimeBand.MIDDAY, 30_000, 1, 7)
        assertThat(v.size).isEqualTo(StopToStopFeatures.NAMES.size)
    }

    @Test
    fun `missing trip start and direction become minus one`() {
        val v = StopToStopFeatures.vector(3, 42.0, DayType.WEEKDAY, TimeBand.MIDDAY, null, null, 7)
        assertThat(v[StopToStopFeatures.NAMES.indexOf("tripStartSec")]).isEqualTo(-1.0)
        assertThat(v[StopToStopFeatures.NAMES.indexOf("directionId")]).isEqualTo(-1.0)
    }

    @Test
    fun `weekday and saturday differ`() {
        val a = StopToStopFeatures.vector(3, 42.0, DayType.WEEKDAY, TimeBand.MIDDAY, 30_000, 1, 7)
        val b = StopToStopFeatures.vector(3, 42.0, DayType.SATURDAY, TimeBand.MIDDAY, 30_000, 1, 7)
        assertThat(a.toList()).isNotEqualTo(b.toList())
    }
}
