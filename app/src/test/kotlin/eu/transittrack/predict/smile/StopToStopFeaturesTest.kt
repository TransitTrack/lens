package eu.transittrack.predict.smile

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
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
    fun `featuresFor buckets by the service day and maps unknown routes to minus one`() {
        val zone = ZoneId.of("Europe/Bucharest")
        // Saturday service day, 08:00 local: Saturday morning peak.
        val v =
            StopToStopFeatures.featuresFor(
                serviceDate = LocalDate.of(2026, 10, 17),
                zone = zone,
                leftPreviousStopAt = Instant.parse("2026-10-17T05:00:00Z"),
                stopPathIndex = 3,
                seedSec = 42.0,
                tripStartSec = 28_000,
                directionId = 1,
                routeId = "unknown",
                routeIndex = mapOf("R1" to 0),
            )
        assertThat(v.toList())
            .isEqualTo(StopToStopFeatures.vector(3, 42.0, DayType.SATURDAY, TimeBand.MORNING_PEAK, 28_000, 1, -1).toList())
    }

    @Test
    fun `weekday and saturday differ`() {
        val a = StopToStopFeatures.vector(3, 42.0, DayType.WEEKDAY, TimeBand.MIDDAY, 30_000, 1, 7)
        val b = StopToStopFeatures.vector(3, 42.0, DayType.SATURDAY, TimeBand.MIDDAY, 30_000, 1, 7)
        assertThat(a.toList()).isNotEqualTo(b.toList())
    }
}
