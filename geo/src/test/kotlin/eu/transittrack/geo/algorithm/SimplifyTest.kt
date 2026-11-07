package eu.transittrack.geo.algorithm

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import eu.transittrack.geo.Position
import kotlin.test.Test

class SimplifyTest {
    @Test
    fun `drops collinear points and keeps endpoints`() {
        val pts = listOf(Position(0.0, 0.0), Position(1.0, 1.0), Position(2.0, 2.0), Position(3.0, 3.0))
        assertThat(simplify(pts, 0.001)).containsExactly(pts.first(), pts.last())
    }

    @Test
    fun `keeps a point that deviates more than the tolerance`() {
        val pts = listOf(Position(0.0, 0.0), Position(1.0, 0.5), Position(2.0, 0.0))
        assertThat(simplify(pts, 0.1)).isEqualTo(pts)
        assertThat(simplify(pts, 1.0)).containsExactly(pts.first(), pts.last())
    }

    @Test
    fun `short inputs are returned unchanged`() {
        val pts = listOf(Position(0.0, 0.0), Position(1.0, 1.0))
        assertThat(simplify(pts, 1.0)).isEqualTo(pts)
    }
}
