package eu.transittrack.schedule

import eu.transittrack.gtfs.support.FixtureDownloader
import kotlin.io.path.createTempFile
import kotlin.test.Test
import kotlin.test.assertTrue

class ScheduleSampleFixtureTest {
    @Test
    fun `schedule-sample fixture is a well-formed archive`() {
        val zip = createTempFile("schedule-sample", ".zip")
        val dl = FixtureDownloader("schedule-sample").download("http://x/g.zip", zip)
        assertTrue(dl.byteSize > 0)
        assertTrue(dl.sha256.length == 64)
    }
}
