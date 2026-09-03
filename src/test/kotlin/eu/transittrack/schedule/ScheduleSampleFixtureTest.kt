package eu.transittrack.schedule

import kotlin.io.path.createTempFile
import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.hasLength
import assertk.assertions.isGreaterThan

import eu.transittrack.gtfs.support.FixtureDownloader

class ScheduleSampleFixtureTest {
    @Test
    fun `schedule-sample fixture is a well-formed archive`() {
        val zip = createTempFile("schedule-sample", ".zip")
        val dl = FixtureDownloader("schedule-sample").download("http://x/g.zip", zip)
        assertThat(dl.byteSize).isGreaterThan(0L)
        assertThat(dl.sha256).hasLength(64)
    }
}
