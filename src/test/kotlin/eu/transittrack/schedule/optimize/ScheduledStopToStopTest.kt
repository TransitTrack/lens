package eu.transittrack.schedule.optimize

import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull

import eu.transittrack.schedule.model.ScheduleTime

class ScheduledStopToStopTest {
    private fun point(
        index: Int,
        travel: Int?,
        dwell: Int?,
    ) = ScheduleTime(
        revisionId = 1L,
        tripId = 1L,
        stopPathIndex = index,
        arrivalSec = null,
        departureSec = null,
        interpolated = false,
        schedTravelTimeSec = travel,
        schedDwellTimeSec = dwell,
    )

    private val schedule = listOf(point(0, null, 30), point(1, 300, 20), point(2, 240, 15), point(3, null, 0))

    @Test
    fun `stop path 1 is measured from leaving the first stop, so it carries no dwell`() {
        assertThat(scheduledStopToStopSec(schedule, 1)).isEqualTo(300)
    }

    @Test
    fun `later stop paths include the dwell at the stop before them`() {
        assertThat(scheduledStopToStopSec(schedule, 2)).isEqualTo(240 + 20)
    }

    @Test
    fun `no scheduled travel time means no counterpart`() {
        assertThat(scheduledStopToStopSec(schedule, 3)).isNull()
    }
}
