package eu.transittrack.geo

import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue

class BBoxTest {
    @Test
    fun `of points spans the bounding box`() {
        val b = BBox.of(listOf(Point(10.0, 20.0), Point(12.0, 18.0), Point(11.0, 25.0)))!!
        assertThat(b.minLat).isEqualTo(10.0)
        assertThat(b.maxLat).isEqualTo(12.0)
        assertThat(b.minLng).isEqualTo(18.0)
        assertThat(b.maxLng).isEqualTo(25.0)
    }

    @Test
    fun `of points is null when given nothing`() {
        assertThat(BBox.of(emptyList<Point>())).isNull()
    }

    @Test
    fun `union of boxes skips nulls`() {
        val a = BBox.of(listOf(Point(10.0, 20.0), Point(11.0, 21.0)))
        val b = BBox.of(listOf(Point(9.0, 19.0), Point(15.0, 30.0)))
        val u = BBox.union(listOf(null, a, b, null))!!
        assertThat(u.minLat).isEqualTo(9.0)
        assertThat(u.maxLat).isEqualTo(15.0)
        assertThat(u.minLng).isEqualTo(19.0)
        assertThat(u.maxLng).isEqualTo(30.0)
    }

    @Test
    fun `union of nothing is null`() {
        assertThat(BBox.union(listOf(null, null))).isNull()
    }

    @Test
    fun `union merges altitude bounds when present`() {
        val a = BBox(0.0, 0.0, 1.0, 1.0, minAlt = 5.0, maxAlt = 10.0)
        val b = BBox(0.5, 0.5, 2.0, 2.0)
        assertThat(a.union(b)).isEqualTo(BBox(0.0, 0.0, 2.0, 2.0, 5.0, 10.0))
    }

    @Test
    fun `isWithinDistance is true inside and just outside the box`() {
        val box = BBox(17.0, 51.0, 17.1, 51.1)
        assertThat(box.isWithinDistance(Point(51.05, 17.05), 0.0)).isTrue()
        // ~111 m north of the box
        assertThat(box.isWithinDistance(Point(51.101, 17.05), 150.0)).isTrue()
        assertThat(box.isWithinDistance(Point(51.101, 17.05), 50.0)).isFalse()
    }
}
