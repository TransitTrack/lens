package eu.transittrack

import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue

class GeoExtentTest {
    @Test
    fun `fresh extent is empty`() {
        assertThat(Extent().isEmpty).isTrue()
    }

    @Test
    fun `of points spans the bounding box`() {
        val e = Extent.of(listOf(Point(10.0, 20.0), Point(12.0, 18.0), Point(11.0, 25.0)))
        assertThat(e.isEmpty).isFalse()
        assertThat(e.minLat).isEqualTo(10.0)
        assertThat(e.maxLat).isEqualTo(12.0)
        assertThat(e.minLon).isEqualTo(18.0)
        assertThat(e.maxLon).isEqualTo(25.0)
    }

    @Test
    fun `of points is empty when given nothing`() {
        assertThat(Extent.of(emptyList()).isEmpty).isTrue()
    }

    @Test
    fun `ofExtents unions children and skips empty ones`() {
        val a = Extent.of(listOf(Point(10.0, 20.0), Point(11.0, 21.0)))
        val b = Extent.of(listOf(Point(9.0, 19.0), Point(15.0, 30.0)))
        val u = Extent.ofExtents(listOf(Extent(), a, b, Extent()))
        assertThat(u.minLat).isEqualTo(9.0)
        assertThat(u.maxLat).isEqualTo(15.0)
        assertThat(u.minLon).isEqualTo(19.0)
        assertThat(u.maxLon).isEqualTo(30.0)
    }
}
