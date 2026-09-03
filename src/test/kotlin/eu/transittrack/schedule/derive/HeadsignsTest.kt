package eu.transittrack.schedule.derive

import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.isEqualTo

class HeadsignsTest {
    @Test fun `trip headsign wins`() = assertThat(Headsigns.resolve("To Centre", "Stop HS")).isEqualTo("To Centre")

    @Test fun `blank trip headsign falls to stop headsign`() = assertThat(Headsigns.resolve("  ", "Stop HS")).isEqualTo("Stop HS")

    @Test fun `null trip headsign falls to stop headsign`() = assertThat(Headsigns.resolve(null, "Stop HS")).isEqualTo("Stop HS")

    @Test fun `both blank yields Loop`() = assertThat(Headsigns.resolve(null, "")).isEqualTo("Loop")
}
