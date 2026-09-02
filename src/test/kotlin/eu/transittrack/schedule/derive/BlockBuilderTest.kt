package eu.transittrack.schedule.derive

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BlockBuilderTest {
    @Test
    fun `orders trips and computes layover and no deadhead`() {
        val t1 = BlockTripInput(1, "B1", "WK", "RA", 28800, 30600, "S1", "S4") // 08:00-08:30
        val t4 = BlockTripInput(2, "B1", "WK", "RA", 31200, 33000, "S4", "S1") // 08:40-09:10
        val res = BlockBuilder.build(listOf(t4, t1))
        assertEquals(1, res.size)
        val b = res.single()
        assertEquals("B1", b.blockId)
        assertEquals(2, b.tripCount)
        assertEquals(28800, b.startTimeSec)
        assertEquals(33000, b.endTimeSec)
        assertEquals(listOf("RA"), b.routeIds)
        val u1 = b.tripUpdates.first { it.schedTripId == 1L }
        assertEquals(0, u1.listIndex)
        assertEquals(600, u1.layoverAfterSec) // 31200 - 30600
        assertEquals(false, u1.deadheadAfter) // S4 == S4
        val u4 = b.tripUpdates.first { it.schedTripId == 2L }
        assertEquals(1, u4.listIndex)
        assertNull(u4.layoverAfterSec)
        assertNull(u4.deadheadAfter)
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
                .first { it.schedTripId == 1L }
        assertEquals(true, u.deadheadAfter)
    }

    @Test
    fun `same block id under two services is two blocks`() {
        val a = BlockTripInput(1, "B", "WK", "R", 0, 100, "X", "Y")
        val b = BlockTripInput(2, "B", "SAT", "R", 0, 100, "X", "Y")
        assertEquals(2, BlockBuilder.build(listOf(a, b)).size)
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
        assertEquals(0, u.listIndex)
        assertNull(u.layoverAfterSec)
    }

    @Test
    fun `route ids are distinct in first-visit order`() {
        val a = BlockTripInput(1, "B", "WK", "R2", 0, 100, "X", "Y")
        val b = BlockTripInput(2, "B", "WK", "R1", 200, 300, "Y", "Z")
        val c = BlockTripInput(3, "B", "WK", "R2", 400, 500, "Z", "W")
        assertEquals(listOf("R2", "R1"), BlockBuilder.build(listOf(a, b, c)).single().routeIds)
    }
}
