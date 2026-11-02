package eu.transittrack.schedule.derive

import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue

import eu.transittrack.ScheduleProperties

class InferredBlockBuilderTest {
    private companion object {
        const val TRAM = 0
        const val BUS = 3
    }

    private fun options(
        allowDeadhead: Boolean = true,
        maxDeadheadGapSec: Int = 1800,
        maxLayoverSec: Int = 5400,
        sameTerminalRadiusM: Double = 200.0,
        deadheadSpeedMps: Double = 5.0,
    ) = ScheduleProperties.InferredBlocks(
        enabled = true,
        allowDeadhead = allowDeadhead,
        maxDeadheadGapSec = maxDeadheadGapSec,
        maxLayoverSec = maxLayoverSec,
        sameTerminalRadiusM = sameTerminalRadiusM,
        deadheadSpeedMps = deadheadSpeedMps,
    )

    // Near 51.1°N one thousandth of a degree of latitude is ~111 m.
    private fun stopAt(
        lat: Double?,
        lon: Double? = 17.0,
        parentStation: String? = null,
    ) = InferredBlockStop(parentStation = parentStation, lat = lat, lon = if (lat == null) null else lon)

    private fun List<BlockResult>.blockOf(tripRowId: Long) = first { it.tripUpdates.any { u -> u.tripRowId == tripRowId } }

    private fun BlockResult.rowIds() = tripUpdates.map { it.tripRowId }

    @Test
    fun `chains same-stop continuation into one block`() {
        val out = InferredBlockTripInput(1, "T1", "WK", "R1", 10, 0, 100, "X", "Y")
        val back = InferredBlockTripInput(2, "T2", "WK", "R1", 11, 200, 300, "Y", "X")
        val result = InferredBlockBuilder.build(listOf(out, back), options())
        assertThat(result).hasSize(1)
        assertThat(result.single().tripCount).isEqualTo(2)
    }

    @Test
    fun `block id is the service plus the first trip id`() {
        val out = InferredBlockTripInput(1, "T1", "WK", "R1", 10, 0, 100, "X", "Y")
        val back = InferredBlockTripInput(2, "T2", "WK", "R1", 11, 200, 300, "Y", "X")

        val result = InferredBlockBuilder.build(listOf(out, back), options())

        assertThat(result.single().blockId).isEqualTo("inferred:WK:T1")
    }

    @Test
    fun `block ids are identical across repeated builds`() {
        val trips =
            listOf(
                InferredBlockTripInput(1, "T1", "WK", "R1", 10, 0, 100, "X", "Y"),
                InferredBlockTripInput(2, "T2", "WK", "R1", 11, 200, 300, "Y", "X"),
                InferredBlockTripInput(3, "T3", "SA", "R2", 20, 0, 100, "Q", "W"),
            )

        val first = idsByTrips(InferredBlockBuilder.build(trips, options()))
        val second = idsByTrips(InferredBlockBuilder.build(trips, options()))

        assertThat(second).isEqualTo(first)
    }

    @Test
    fun `chains same route ahead of an earlier different-route candidate`() {
        // current ends at stop Y at t=100.
        val current = InferredBlockTripInput(1, "T1", "WK", "R1", 10, 0, 100, "X", "Y")
        // A different route's trip starts right away at the same stop (earliest by start time).
        val otherRoute = InferredBlockTripInput(2, "T2", "WK", "R2", 20, 110, 200, "Y", "Z")
        // Same route (R1) continues the round trip, but starts a bit later.
        val sameRoute = InferredBlockTripInput(3, "T3", "WK", "R1", 12, 150, 250, "Y", "X")

        val result = InferredBlockBuilder.build(listOf(current, otherRoute, sameRoute), options())

        // current + sameRoute chain together (R1 -> R1); otherRoute (R2) starts its own block
        // because same-route matching is resolved for every route before any cross-route pass.
        val chainWithCurrent = result.blockOf(1)
        assertThat(chainWithCurrent.tripCount).isEqualTo(2)
        assertThat(chainWithCurrent.routeIds).isEqualTo(listOf("R1"))

        val otherRouteChain = result.blockOf(2)
        assertThat(otherRouteChain.tripCount).isEqualTo(1)
    }

    @Test
    fun `a route chain continues onto lone trips of other routes`() {
        // R1 has two trips that chain together cleanly on their own, ending at X at t=200.
        val r1a = InferredBlockTripInput(1, "T1", "WK", "R1", 10, 0, 100, "X", "Y", routeType = BUS)
        val r1b = InferredBlockTripInput(2, "T2", "WK", "R1", 10, 100, 200, "Y", "X", routeType = BUS)
        // R2 has a single trip leaving X right after; R3 a single trip leaving where R2 ends.
        val r2 = InferredBlockTripInput(3, "T3", "WK", "R2", 20, 210, 300, "X", "Z", routeType = BUS)
        val r3 = InferredBlockTripInput(4, "T4", "WK", "R3", 30, 300, 400, "Z", "W", routeType = BUS)

        val result = InferredBlockBuilder.build(listOf(r1a, r1b, r2, r3), options())

        assertThat(result).hasSize(1)
        assertThat(result.single().rowIds()).containsExactly(1L, 2L, 3L, 4L)
        assertThat(result.single().routeIds).isEqualTo(listOf("R1", "R2", "R3"))
    }

    @Test
    fun `a route chain joins onto another route's multi-trip chain`() {
        // Interlining: the vehicle works R1 in the morning, then R2 from the shared terminal X.
        val r1a = InferredBlockTripInput(1, "T1", "WK", "R1", 10, 0, 100, "X", "Y", routeType = BUS)
        val r1b = InferredBlockTripInput(2, "T2", "WK", "R1", 11, 110, 200, "Y", "X", routeType = BUS)
        val r2a = InferredBlockTripInput(3, "T3", "WK", "R2", 20, 210, 300, "X", "Z", routeType = BUS)
        val r2b = InferredBlockTripInput(4, "T4", "WK", "R2", 21, 310, 400, "Z", "X", routeType = BUS)

        val result = InferredBlockBuilder.build(listOf(r1a, r1b, r2a, r2b), options(allowDeadhead = false))

        assertThat(result).hasSize(1)
        assertThat(result.single().rowIds()).containsExactly(1L, 2L, 3L, 4L)
        assertThat(result.single().routeIds).isEqualTo(listOf("R1", "R2"))
    }

    @Test
    fun `chains a lone trip onto another route's trip at the same stop`() {
        val current = InferredBlockTripInput(1, "T1", "WK", "R1", 10, 0, 100, "X", "Y", routeType = BUS)
        val otherRoute = InferredBlockTripInput(2, "T2", "WK", "R2", 20, 110, 200, "Y", "Z", routeType = BUS)

        val result = InferredBlockBuilder.build(listOf(current, otherRoute), options(allowDeadhead = false))

        assertThat(result).hasSize(1)
        assertThat(result.single().tripCount).isEqualTo(2)
    }

    @Test
    fun `does not link routes of different route types`() {
        // A bus route ends at Y just before a tram route leaves Y.
        val bus = InferredBlockTripInput(1, "T1", "WK", "R1", 10, 0, 100, "X", "Y", routeType = BUS)
        val tram = InferredBlockTripInput(2, "T2", "WK", "R2", 20, 110, 200, "Y", "Z", routeType = TRAM)

        val result = InferredBlockBuilder.build(listOf(bus, tram), options())

        assertThat(result).hasSize(2)
    }

    @Test
    fun `links routes whose extended route types share a basic type`() {
        // Basic "bus" (3) and extended "local bus service" (704) are the same kind of vehicle.
        val bus = InferredBlockTripInput(1, "T1", "WK", "R1", 10, 0, 100, "X", "Y", routeType = BUS)
        val localBus = InferredBlockTripInput(2, "T2", "WK", "R2", 20, 110, 200, "Y", "Z", routeType = 704)

        val result = InferredBlockBuilder.build(listOf(bus, localBus), options())

        assertThat(result).hasSize(1)
    }

    @Test
    fun `does not link extended route types with different basic types`() {
        // Extended "tram service" (900) and "trolleybus service" (800).
        val tram = InferredBlockTripInput(1, "T1", "WK", "R1", 10, 0, 100, "X", "Y", routeType = 900)
        val trolleybus = InferredBlockTripInput(2, "T2", "WK", "R2", 20, 110, 200, "Y", "Z", routeType = 800)

        val result = InferredBlockBuilder.build(listOf(tram, trolleybus), options())

        assertThat(result).hasSize(2)
    }

    @Test
    fun `does not link routes when a route type is unknown`() {
        val known = InferredBlockTripInput(1, "T1", "WK", "R1", 10, 0, 100, "X", "Y", routeType = BUS)
        val unknown = InferredBlockTripInput(2, "T2", "WK", "R2", 20, 110, 200, "Y", "Z", routeType = null)

        val result = InferredBlockBuilder.build(listOf(known, unknown), options())

        assertThat(result).hasSize(2)
    }

    @Test
    fun `sibling platforms of one parent station form one terminal`() {
        val out = InferredBlockTripInput(1, "T1", "WK", "R1", 10, 0, 100, "X", "Y-arr")
        val back = InferredBlockTripInput(2, "T2", "WK", "R1", 11, 200, 300, "Y-dep", "X")
        val stops = mapOf("Y-arr" to stopAt(null, parentStation = "Y"), "Y-dep" to stopAt(null, parentStation = "Y"))

        val result = InferredBlockBuilder.build(listOf(out, back), options(allowDeadhead = false), stops)

        assertThat(result).hasSize(1)
        assertThat(result.single().rowIds()).containsExactly(1L, 2L)
    }

    @Test
    fun `stops within the terminal radius form one terminal`() {
        val out = InferredBlockTripInput(1, "T1", "WK", "R1", 10, 0, 100, "X", "Y-arr")
        val back = InferredBlockTripInput(2, "T2", "WK", "R1", 11, 200, 300, "Y-dep", "X")
        // ~50 m apart, no shared parent station.
        val stops = mapOf("Y-arr" to stopAt(51.10000), "Y-dep" to stopAt(51.10045))

        val result = InferredBlockBuilder.build(listOf(out, back), options(allowDeadhead = false), stops)

        assertThat(result).hasSize(1)
    }

    @Test
    fun `a platform change within one terminal is not flagged as a deadhead`() {
        val out = InferredBlockTripInput(1, "T1", "WK", "R1", 10, 0, 100, "X", "Y-arr")
        val back = InferredBlockTripInput(2, "T2", "WK", "R1", 11, 200, 300, "Y-dep", "X")
        val stops = mapOf("Y-arr" to stopAt(51.10000), "Y-dep" to stopAt(51.10045))

        val result = InferredBlockBuilder.build(listOf(out, back), options(allowDeadhead = false), stops)

        assertThat(
            result
                .single()
                .tripUpdates
                .first()
                .deadheadAfter!!,
        ).isFalse()
    }

    @Test
    fun `stops beyond the terminal radius do not chain without deadhead`() {
        val out = InferredBlockTripInput(1, "T1", "WK", "R1", 10, 0, 100, "X", "Y")
        val back = InferredBlockTripInput(2, "T2", "WK", "R1", 11, 200, 300, "Z", "X")
        // ~300 m apart.
        val stops = mapOf("Y" to stopAt(51.1000), "Z" to stopAt(51.1027))

        val result = InferredBlockBuilder.build(listOf(out, back), options(allowDeadhead = false), stops)

        assertThat(result).hasSize(2)
    }

    @Test
    fun `same-stop continuation beyond max layover starts a new block`() {
        // Morning-peak vehicle ends at Y at 08:30; the next departure from Y is at 15:00.
        val am = InferredBlockTripInput(1, "T1", "WK", "R1", 10, 27_000, 30_600, "X", "Y")
        val pm = InferredBlockTripInput(2, "T2", "WK", "R1", 11, 54_000, 57_600, "Y", "X")

        val result = InferredBlockBuilder.build(listOf(am, pm), options(maxLayoverSec = 5400))

        assertThat(result).hasSize(2)
    }

    @Test
    fun `same-stop continuation at exactly max layover still chains`() {
        val out = InferredBlockTripInput(1, "T1", "WK", "R1", 10, 0, 100, "X", "Y")
        val back = InferredBlockTripInput(2, "T2", "WK", "R1", 11, 5500, 5600, "Y", "X")

        val result = InferredBlockBuilder.build(listOf(out, back), options(maxLayoverSec = 5400))

        assertThat(result).hasSize(1)
    }

    @Test
    fun `prefers a same-stop successor over an earlier deadhead`() {
        // A ends at Y, B ends at Z (~300 m away) at the same time.
        val a = InferredBlockTripInput(1, "A", "WK", "R1", 10, 0, 100, "X", "Y")
        val b = InferredBlockTripInput(2, "B", "WK", "R1", 11, 5, 100, "W", "Z")
        // C leaves Z first (a feasible 100 s deadhead for A); D leaves Y a little later.
        val c = InferredBlockTripInput(3, "C", "WK", "R1", 12, 200, 300, "Z", "W")
        val d = InferredBlockTripInput(4, "D", "WK", "R1", 13, 250, 350, "Y", "X")
        val stops = mapOf("Y" to stopAt(51.1000), "Z" to stopAt(51.1027))

        val result = InferredBlockBuilder.build(listOf(a, b, c, d), options(sameTerminalRadiusM = 50.0), stops)

        assertThat(result.blockOf(1).rowIds()).containsExactly(1L, 4L)
        assertThat(result.blockOf(2).rowIds()).containsExactly(2L, 3L)
    }

    @Test
    fun `rejects a deadhead too short for the distance between stops`() {
        val out = InferredBlockTripInput(1, "T1", "WK", "R1", 10, 0, 100, "X", "Y")
        // ~3 km away, 10 s later.
        val next = InferredBlockTripInput(2, "T2", "WK", "R1", 11, 110, 300, "Z", "X")
        val stops = mapOf("Y" to stopAt(51.100), "Z" to stopAt(51.127))

        val result = InferredBlockBuilder.build(listOf(out, next), options(), stops)

        assertThat(result).hasSize(2)
    }

    @Test
    fun `rejects a deadhead when stop coordinates are unknown`() {
        val out = InferredBlockTripInput(1, "T1", "WK", "R1", 10, 0, 100, "X", "Y")
        val next = InferredBlockTripInput(2, "T2", "WK", "R1", 11, 700, 800, "Z", "X")

        val result = InferredBlockBuilder.build(listOf(out, next), options())

        assertThat(result).hasSize(2)
    }

    @Test
    fun `accepts a deadhead whose gap covers the distance`() {
        val out = InferredBlockTripInput(1, "T1", "WK", "R1", 10, 0, 100, "X", "Y", routeType = BUS)
        // ~1 km away needs 200 s at 5 m/s; the gap is 600 s.
        val next = InferredBlockTripInput(2, "T2", "WK", "R2", 20, 700, 800, "Z", "X", routeType = BUS)
        val stops = mapOf("Y" to stopAt(51.100), "Z" to stopAt(51.109))

        val result = InferredBlockBuilder.build(listOf(out, next), options(), stops)

        assertThat(result).hasSize(1)
        assertThat(
            result
                .single()
                .tripUpdates
                .first()
                .deadheadAfter!!,
        ).isTrue()
    }

    @Test
    fun `rejects a deadhead longer than the max deadhead gap`() {
        val out = InferredBlockTripInput(1, "T1", "WK", "R1", 10, 0, 100, "X", "Y")
        val next = InferredBlockTripInput(2, "T2", "WK", "R1", 11, 2000, 2100, "Z", "X")
        val stops = mapOf("Y" to stopAt(51.100), "Z" to stopAt(51.109))

        val result = InferredBlockBuilder.build(listOf(out, next), options(maxDeadheadGapSec = 1800), stops)

        assertThat(result).hasSize(2)
    }

    @Test
    fun `does not deadhead when deadheads are disabled`() {
        val out = InferredBlockTripInput(1, "T1", "WK", "R1", 10, 0, 100, "X", "Y")
        val next = InferredBlockTripInput(2, "T2", "WK", "R1", 11, 700, 800, "Z", "X")
        val stops = mapOf("Y" to stopAt(51.100), "Z" to stopAt(51.109))

        val result = InferredBlockBuilder.build(listOf(out, next), options(allowDeadhead = false), stops)

        assertThat(result).hasSize(2)
    }

    private fun idsByTrips(results: List<BlockResult>) = results.associate { it.rowIds() to it.blockId }
}
