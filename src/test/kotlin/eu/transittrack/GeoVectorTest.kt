package eu.transittrack

import kotlin.math.PI
import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import assertk.assertions.isZero

/**
 * Exercises [Vector] and the point/segment geometry helpers in `Geo.kt`.
 *
 * Coordinates sit around Wrocław (lat ≈ 51°, lon ≈ 17°). At that latitude the equirectangular
 * approximation gives ≈ 1112 m per 0.01° of latitude and ≈ 700 m per 0.01° of longitude.
 */
class GeoVectorTest {
    private val south = Point(51.0, 17.0)
    private val north = Point(51.01, 17.0)
    private val northSegment = Vector(south, north)

    @Test
    fun `length is the ground distance between endpoints`() {
        assertThat(northSegment.length()).isCloseTo(1112.0, 5.0)
    }

    @Test
    fun `Point-to-Point distance uses the equirectangular approximation`() {
        assertThat(south.distance(north)).isCloseTo(1112.0, 5.0)
        assertThat(south.distance(Point(51.0, 17.0))).isZero()
        // 0.01 deg of longitude is shorter than 0.01 deg of latitude away from the equator.
        assertThat(south.distance(Point(51.0, 17.01))).isCloseTo(700.0, 10.0)
    }

    @Test
    fun `distance to a point on the segment is ~zero and matches halfway along`() {
        val mid = Point(51.005, 17.0)
        assertThat(northSegment.distance(mid)).isCloseTo(0.0, 5.0)
        assertThat(northSegment.matchDistanceAlongVector(mid)).isCloseTo(556.0, 5.0)
    }

    @Test
    fun `distance to a point beside the segment is the perpendicular offset`() {
        val beside = Point(51.005, 17.01)
        assertThat(northSegment.distance(beside)).isCloseTo(700.0, 10.0)
        assertThat(northSegment.matchDistanceAlongVector(beside)).isCloseTo(556.0, 5.0)
    }

    @Test
    fun `a point past the start clamps to the start`() {
        val before = Point(50.99, 17.0)
        assertThat(northSegment.distance(before)).isCloseTo(1112.0, 5.0)
        assertThat(northSegment.matchDistanceAlongVector(before)).isZero()
    }

    @Test
    fun `a point past the end clamps to the end`() {
        val after = Point(51.02, 17.0)
        assertThat(northSegment.distance(after)).isCloseTo(1112.0, 5.0)
        assertThat(northSegment.matchDistanceAlongVector(after)).isCloseTo(northSegment.length(), 1e-6)
    }

    @Test
    fun `zero-length vector degrades gracefully`() {
        val degenerate = Vector(south, Point(51.0, 17.0))
        assertThat(degenerate.length()).isZero()
        assertThat(degenerate.distance(north)).isCloseTo(1112.0, 5.0)
        assertThat(degenerate.matchDistanceAlongVector(north)).isZero()
    }

    @Test
    fun `beginning end and middle carve the segment by distance`() {
        val head = northSegment.beginning(556.0)
        assertThat(head.l1).isEqualTo(south)
        assertThat(head.length()).isCloseTo(556.0, 5.0)

        val tail = northSegment.end(556.0)
        assertThat(tail.l2).isEqualTo(north)
        assertThat(tail.length()).isCloseTo(556.0, 5.0)

        val core = northSegment.middle(278.0, 834.0)
        assertThat(core.length()).isCloseTo(556.0, 5.0)
    }

    @Test
    fun `locAlongVector interpolates linearly`() {
        val quarter = northSegment.locAlongVector(278.0)
        assertThat(quarter.lat).isCloseTo(51.0025, 1e-4)
        assertThat(quarter.lon).isCloseTo(17.0, 1e-9)
    }

    @Test
    fun `heading is degrees clockwise from north`() {
        assertThat(Vector(south, north).heading()).isCloseTo(0.0, 1.0)
        assertThat(Vector(south, Point(51.0, 17.01)).heading()).isCloseTo(90.0, 1.0)
        assertThat(Vector(south, Point(51.0, 16.99)).heading()).isCloseTo(-90.0, 1.0)
    }

    @Test
    fun `angle is radians counterclockwise from east`() {
        assertThat(Vector(south, Point(51.0, 17.01)).angle()).isCloseTo(0.0, 0.02)
        assertThat(Vector(south, north).angle()).isCloseTo(PI / 2, 0.02)
    }
}
