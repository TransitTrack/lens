package eu.transittrack.schedule.derive

import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan

import eu.transittrack.Point
import eu.transittrack.Polyline
import eu.transittrack.haversineMeters

class ShapeProjectionTest {
    private val line = Polyline(listOf(Point(51.100, 17.000), Point(51.110, 17.000), Point(51.120, 17.000)))

    @Test
    fun `cumulative distances are monotonic and start at zero`() {
        assertThat(line.cumulative[0]).isEqualTo(0.0)
        assertThat(line.cumulative[1]).isLessThan(line.cumulative[2])
        assertThat(line.lengthM).isCloseTo(haversineMeters(51.100, 17.000, 51.120, 17.000), 5.0)
    }

    @Test
    fun `project a point near the middle vertex`() {
        val pr = line.project(Point(51.1101, 17.0005))
        assertThat(pr.distanceAlong).isCloseTo(line.cumulative[1], 30.0)
        assertThat(pr.deviationM).isLessThan(60.0)
    }

    @Test
    fun `project a far-off point reports large deviation`() {
        val pr = line.project(Point(51.110, 17.050))
        assertThat(pr.deviationM).isGreaterThan(1000.0)
    }

    @Test
    fun `pointAt endpoints`() {
        assertThat(line.pointAt(0.0).lat).isCloseTo(51.100, 1e-9)
        assertThat(line.pointAt(line.lengthM).lat).isCloseTo(51.120, 1e-6)
    }

    @Test
    fun `slice keeps intermediate vertices`() {
        val s = ShapeProjection.slice(line, 0.0, line.lengthM)
        assertThat(s).hasSize(3)
        assertThat(s[1].lat).isCloseTo(51.110, 1e-9)
    }

    @Test
    fun `slice with no vertex between yields two points`() {
        val s = ShapeProjection.slice(line, line.cumulative[1] + 10.0, line.cumulative[2] - 10.0)
        assertThat(s).hasSize(2)
    }

    @Test
    fun `straightLine is the two endpoints`() {
        val s = ShapeProjection.straightLine(Point(1.0, 2.0), Point(3.0, 4.0))
        assertThat(s).isEqualTo(listOf(Point(1.0, 2.0), Point(3.0, 4.0)))
    }

    @Test
    fun `single-point polyline has zero length`() {
        assertThat(Polyline(listOf(Point(51.0, 17.0))).lengthM).isEqualTo(0.0)
    }

    // --- L-shaped (right-angle) polyline: east along a parallel, then north along a meridian ---

    private val elbow = Point(51.100, 17.020)
    private val lShape = Polyline(listOf(Point(51.100, 17.000), elbow, Point(51.120, 17.020)))

    @Test
    fun `L-shaped polyline length is the sum of both legs`() {
        val leg1 = haversineMeters(51.100, 17.000, 51.100, 17.020)
        val leg2 = haversineMeters(51.100, 17.020, 51.120, 17.020)
        assertThat(lShape.cumulative[1]).isCloseTo(leg1, 1.0)
        assertThat(lShape.lengthM).isCloseTo(leg1 + leg2, 1.0)
    }

    @Test
    fun `project a point near the elbow lands at the elbow distance`() {
        // Just inside the corner — equally close to both legs, so a naive per-segment
        // scan must still report the corner's cumulative distance, not leg 2's end.
        val pr = lShape.project(Point(51.1005, 17.0195))
        assertThat(pr.distanceAlong).isCloseTo(lShape.cumulative[1], 80.0)
        assertThat(pr.deviationM).isLessThan(80.0)
    }

    @Test
    fun `project on the first leg of an L stays on the first leg`() {
        val pr = lShape.project(Point(51.100, 17.010))
        assertThat(pr.distanceAlong).isCloseTo(lShape.cumulative[1] / 2.0, 5.0)
        assertThat(pr.deviationM).isLessThan(1.0)
    }

    @Test
    fun `project on the second leg of an L is past the elbow`() {
        val pr = lShape.project(Point(51.110, 17.020))
        assertThat(pr.distanceAlong).isGreaterThan(lShape.cumulative[1])
        assertThat(pr.distanceAlong).isLessThan(lShape.lengthM)
    }

    @Test
    fun `a point inside the corner of an L projects onto the shape, not the chord`() {
        // (51.100, 17.000) -> (51.120, 17.020) straight would cut the corner; the corner
        // point itself must measure as on-shape (deviation ~0) at the elbow distance.
        val pr = lShape.project(elbow)
        assertThat(pr.distanceAlong).isCloseTo(lShape.cumulative[1], 1.0)
        assertThat(pr.deviationM).isLessThan(1.0)
    }

    @Test
    fun `slice across the elbow keeps the corner vertex`() {
        val s =
            ShapeProjection.slice(
                lShape,
                lShape.cumulative[1] - 100.0,
                lShape.cumulative[1] + 100.0,
            )
        assertThat(s).hasSize(3)
        assertThat(s[1]).isEqualTo(elbow)
    }

    // --- degenerate polylines ---

    @Test
    fun `duplicate-point polyline has zero length and a safe pointAt`() {
        val degenerate = Polyline(listOf(Point(51.0, 17.0), Point(51.0, 17.0)))
        assertThat(degenerate.lengthM).isEqualTo(0.0)
        assertThat(degenerate.pointAt(0.0)).isEqualTo(Point(51.0, 17.0))
        assertThat(degenerate.pointAt(500.0)).isEqualTo(Point(51.0, 17.0))
        assertThat(degenerate.pointAt(-500.0)).isEqualTo(Point(51.0, 17.0))
    }

    @Test
    fun `projecting onto a duplicate-point polyline reports distance zero`() {
        val degenerate = Polyline(listOf(Point(51.0, 17.0), Point(51.0, 17.0)))
        val pr = degenerate.project(Point(51.01, 17.0))
        assertThat(pr.distanceAlong).isEqualTo(0.0)
        assertThat(pr.deviationM).isGreaterThan(0.0)
    }

    @Test
    fun `slicing a duplicate-point polyline yields two coincident points`() {
        val degenerate = Polyline(listOf(Point(51.0, 17.0), Point(51.0, 17.0)))
        val s = ShapeProjection.slice(degenerate, 0.0, 100.0)
        assertThat(s).isEqualTo(listOf(Point(51.0, 17.0), Point(51.0, 17.0)))
    }
}
