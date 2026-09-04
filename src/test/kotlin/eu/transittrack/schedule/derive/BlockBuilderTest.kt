package eu.transittrack.schedule.derive

import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isNull

class BlockBuilderTest {
    @Test
    fun `orders trips and computes layover and no deadhead`() {
        val t1 = BlockTripInput(1, "B1", "WK", "RA", 28800, 30600, "S1", "S4") // 08:00-08:30
        val t4 = BlockTripInput(2, "B1", "WK", "RA", 31200, 33000, "S4", "S1") // 08:40-09:10
        val res = BlockBuilder.build(listOf(t4, t1))
        assertThat(res).hasSize(1)
        val b = res.single()
        assertThat(b.blockId).isEqualTo("B1")
        assertThat(b.tripCount).isEqualTo(2)
        assertThat(b.startTimeSec).isEqualTo(28800)
        assertThat(b.endTimeSec).isEqualTo(33000)
        assertThat(b.routeIds).isEqualTo(listOf("RA"))
        val u1 = b.tripUpdates.first { it.tripRowId == 1L }
        assertThat(u1.listIndex).isEqualTo(0)
        assertThat(u1.layoverAfterSec).isEqualTo(600) // 31200 - 30600
        assertThat(u1.deadheadAfter).isEqualTo(false) // S4 == S4
        val u4 = b.tripUpdates.first { it.tripRowId == 2L }
        assertThat(u4.listIndex).isEqualTo(1)
        assertThat(u4.layoverAfterSec).isNull()
        assertThat(u4.deadheadAfter).isNull()
    }

    @Test
    fun `deadhead when stops differ`() {
        val a = BlockTripInput(1, "B", "WK", "R", 0, 100, "X", "Y")
        val b = BlockTripInput(2, "B", "WK", "R", 200, 300, "Z", "W")
        val u =
            BlockBuilder
                .build(listOf(a, b))
                .single()
                .tripUpdates
                .first { it.tripRowId == 1L }
        assertThat(u.deadheadAfter).isEqualTo(true)
    }

    @Test
    fun `same block id under two services is two blocks`() {
        val a = BlockTripInput(1, "B", "WK", "R", 0, 100, "X", "Y")
        val b = BlockTripInput(2, "B", "SAT", "R", 0, 100, "X", "Y")
        assertThat(BlockBuilder.build(listOf(a, b))).hasSize(2)
    }

    @Test
    fun `single-trip block has one seq-0 trip and null gaps`() {
        val a = BlockTripInput(1, "B", "WK", "R", 0, 100, "X", "Y")
        val u =
            BlockBuilder
                .build(listOf(a))
                .single()
                .tripUpdates
                .single()
        assertThat(u.listIndex).isEqualTo(0)
        assertThat(u.layoverAfterSec).isNull()
    }

    @Test
    fun `route ids are distinct in first-visit order`() {
        val a = BlockTripInput(1, "B", "WK", "R2", 0, 100, "X", "Y")
        val b = BlockTripInput(2, "B", "WK", "R1", 200, 300, "Y", "Z")
        val c = BlockTripInput(3, "B", "WK", "R2", 400, 500, "Z", "W")
        assertThat(BlockBuilder.build(listOf(a, b, c)).single().routeIds).isEqualTo(listOf("R2", "R1"))
    }
}
