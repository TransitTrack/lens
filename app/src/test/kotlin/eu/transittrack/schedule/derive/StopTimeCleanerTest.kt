package eu.transittrack.schedule.derive

import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue

import eu.transittrack.gtfs.model.StopTime

class StopTimeCleanerTest {
    private fun st(
        seq: Int,
        stopId: String?,
        arr: Int?,
        dep: Int?,
    ) = StopTime(
        1L, tripId = "T", stopSequence = seq, stopId = stopId, arrivalTime = arr, departureTime = dep,
        locationGroupId = null, locationId = null, stopHeadsign = null,
        startPickupDropOffWindow = null, endPickupDropOffWindow = null, pickupType = null,
        dropOffType = null, continuousPickup = null, continuousDropOff = null,
        shapeDistTraveled = null, timepoint = null, pickupBookingRuleId = null, dropOffBookingRuleId = null,
    )

    @Test
    fun `identical consecutive duplicate is dropped`() {
        val out = StopTimeCleaner.clean(listOf(st(1, "A", 100, 100), st(2, "A", 100, 100), st(3, "B", 200, 200)))
        assertThat(out.map { it.stopId }).isEqualTo(listOf("A", "B"))
        assertThat(out[0].waitReconstructed).isFalse()
    }

    @Test
    fun `duplicate with a timeless second row is dropped`() {
        val out = StopTimeCleaner.clean(listOf(st(1, "A", 100, 100), st(2, "A", null, null), st(3, "B", 200, 200)))
        assertThat(out).hasSize(2)
    }

    @Test
    fun `duplicate with differing times becomes one wait stop`() {
        val out = StopTimeCleaner.clean(listOf(st(1, "A", 100, 130), st(2, "A", 140, 160), st(3, "B", 300, 300)))
        assertThat(out).hasSize(2)
        assertThat(out[0].arrivalSec).isEqualTo(100) // first row's arrival
        assertThat(out[0].departureSec).isEqualTo(160) // second row's departure
        assertThat(out[0].stopSequence).isEqualTo(1)
        assertThat(out[0].waitReconstructed).isTrue()
    }

    @Test
    fun `no consecutive duplicates passes straight through`() {
        val out = StopTimeCleaner.clean(listOf(st(1, "A", 100, 100), st(2, "B", 200, 200), st(3, "A", 300, 300)))
        assertThat(out.map { it.stopId }).isEqualTo(listOf("A", "B", "A"))
    }

    @Test
    fun `three consecutive identical fold to one`() {
        val out = StopTimeCleaner.clean(listOf(st(1, "A", 100, 100), st(2, "A", 100, 100), st(3, "A", 100, 100)))
        assertThat(out).hasSize(1)
    }

    @Test
    fun `null stopId rows are preserved`() {
        val out = StopTimeCleaner.clean(listOf(st(1, "A", 100, 100), st(2, null, null, null)))
        assertThat(out).hasSize(2)
    }
}
