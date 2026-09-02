package eu.transittrack

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GeoExtentTest {
    @Test
    fun `fresh extent is empty`() {
        assertTrue(Extent().isEmpty)
    }

    @Test
    fun `of points spans the bounding box`() {
        val e = Extent.of(listOf(Point(10.0, 20.0), Point(12.0, 18.0), Point(11.0, 25.0)))
        assertFalse(e.isEmpty)
        assertEquals(10.0, e.minLat)
        assertEquals(12.0, e.maxLat)
        assertEquals(18.0, e.minLon)
        assertEquals(25.0, e.maxLon)
    }

    @Test
    fun `of points is empty when given nothing`() {
        assertTrue(Extent.of(emptyList()).isEmpty)
    }

    @Test
    fun `ofExtents unions children and skips empty ones`() {
        val a = Extent.of(listOf(Point(10.0, 20.0), Point(11.0, 21.0)))
        val b = Extent.of(listOf(Point(9.0, 19.0), Point(15.0, 30.0)))
        val u = Extent.ofExtents(listOf(Extent(), a, b, Extent()))
        assertEquals(9.0, u.minLat)
        assertEquals(15.0, u.maxLat)
        assertEquals(19.0, u.minLon)
        assertEquals(30.0, u.maxLon)
    }
}
