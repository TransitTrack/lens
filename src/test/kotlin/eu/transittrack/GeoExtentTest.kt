package eu.transittrack

import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.locationtech.jts.geom.Coordinate

class GeoExtentTest {
    @Test
    fun `fresh extent is empty`() {
        assertThat(Extent().isEmpty).isTrue()
    }

    @Test
    fun `of points spans the bounding box`() {
        val e = Extent.of(listOf(Coordinate(20.0, 10.0), Coordinate(18.0, 12.0), Coordinate(25.0, 11.0)))
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
        val a = Extent.of(listOf(Coordinate(20.0, 10.0), Coordinate(21.0, 11.0)))
        val b = Extent.of(listOf(Coordinate(19.0, 9.0), Coordinate(30.0, 15.0)))
        val u = Extent.ofExtents(listOf(Extent(), a, b, Extent()))
        assertThat(u.minLat).isEqualTo(9.0)
        assertThat(u.maxLat).isEqualTo(15.0)
        assertThat(u.minLon).isEqualTo(19.0)
        assertThat(u.maxLon).isEqualTo(30.0)
    }
}
