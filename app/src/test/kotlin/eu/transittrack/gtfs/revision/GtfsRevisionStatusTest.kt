package eu.transittrack.gtfs.revision

import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.doesNotContain
import assertk.assertions.isFalse

class GtfsRevisionStatusTest {
    @Test
    fun `DRAFT exists and is neither terminal nor in-progress`() {
        assertThat(GtfsRevisionStatus.DRAFT.terminal).isFalse()
        assertThat(GtfsRevisionStatus.NON_TERMINAL_IN_PROGRESS).doesNotContain(GtfsRevisionStatus.DRAFT)
    }
}
