package eu.transittrack.schedule.derive

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

import eu.transittrack.Point
import eu.transittrack.Polyline
import eu.transittrack.haversineMeters

class ShapeProjectionTest {
    private val line = Polyline(listOf(Point(51.100, 17.000), Point(51.110, 17.000), Point(51.120, 17.000)))

    @Test
    fun `cumulative distances are monotonic and start at zero`() {
        assertEquals(0.0, line.cumulative[0])
        assertTrue(line.cumulative[1] < line.cumulative[2])
        assertTrue(abs(line.lengthM - haversineMeters(51.100, 17.000, 51.120, 17.000)) < 5.0)
    }

    @Test
    fun `project a point near the middle vertex`() {
        val pr = line.project(Point(51.1101, 17.0005))
        assertTrue(abs(pr.distanceAlong - line.cumulative[1]) < 30.0, "along=${pr.distanceAlong}")
        assertTrue(pr.deviationM < 60.0, "dev=${pr.deviationM}")
    }

    @Test
    fun `project a far-off point reports large deviation`() {
        val pr = line.project(Point(51.110, 17.050))
        assertTrue(pr.deviationM > 1000.0)
    }

    @Test
    fun `pointAt endpoints`() {
        assertEquals(51.100, line.pointAt(0.0).lat, 1e-9)
        assertEquals(51.120, line.pointAt(line.lengthM).lat, 1e-6)
    }

    @Test
    fun `slice keeps intermediate vertices`() {
        val s = ShapeProjection.slice(line, 0.0, line.lengthM)
        assertEquals(3, s.size)
        assertEquals(51.110, s[1].lat, 1e-9)
    }

    @Test
    fun `slice with no vertex between yields two points`() {
        val s = ShapeProjection.slice(line, line.cumulative[1] + 10.0, line.cumulative[2] - 10.0)
        assertEquals(2, s.size)
    }

    @Test
    fun `straightLine is the two endpoints`() {
        val s = ShapeProjection.straightLine(Point(1.0, 2.0), Point(3.0, 4.0))
        assertEquals(listOf(Point(1.0, 2.0), Point(3.0, 4.0)), s)
    }

    @Test
    fun `single-point polyline has zero length`() {
        assertEquals(0.0, Polyline(listOf(Point(51.0, 17.0))).lengthM)
    }

    // --- L-shaped (right-angle) polyline: east along a parallel, then north along a meridian ---

    private val elbow = Point(51.100, 17.020)
    private val lShape = Polyline(listOf(Point(51.100, 17.000), elbow, Point(51.120, 17.020)))

    @Test
    fun `L-shaped polyline length is the sum of both legs`() {
        val leg1 = haversineMeters(51.100, 17.000, 51.100, 17.020)
        val leg2 = haversineMeters(51.100, 17.020, 51.120, 17.020)
        assertEquals(leg1, lShape.cumulative[1], 1.0)
        assertEquals(leg1 + leg2, lShape.lengthM, 1.0)
    }

    @Test
    fun `project a point near the elbow lands at the elbow distance`() {
        // Just inside the corner — equally close to both legs, so a naive per-segment
        // scan must still report the corner's cumulative distance, not leg 2's end.
        val pr = lShape.project(Point(51.1005, 17.0195))
        assertTrue(abs(pr.distanceAlong - lShape.cumulative[1]) < 80.0, "along=${pr.distanceAlong}")
        assertTrue(pr.deviationM < 80.0, "dev=${pr.deviationM}")
    }

    @Test
    fun `project on the first leg of an L stays on the first leg`() {
        val pr = lShape.project(Point(51.100, 17.010))
        assertEquals(lShape.cumulative[1] / 2.0, pr.distanceAlong, 5.0)
        assertTrue(pr.deviationM < 1.0, "dev=${pr.deviationM}")
    }

    @Test
    fun `project on the second leg of an L is past the elbow`() {
        val pr = lShape.project(Point(51.110, 17.020))
        assertTrue(pr.distanceAlong > lShape.cumulative[1], "along=${pr.distanceAlong}")
        assertTrue(pr.distanceAlong < lShape.lengthM, "along=${pr.distanceAlong}")
    }

    @Test
    fun `a point inside the corner of an L projects onto the shape, not the chord`() {
        // (51.100, 17.000) -> (51.120, 17.020) straight would cut the corner; the corner
        // point itself must measure as on-shape (deviation ~0) at the elbow distance.
        val pr = lShape.project(elbow)
        assertEquals(lShape.cumulative[1], pr.distanceAlong, 1.0)
        assertTrue(pr.deviationM < 1.0, "dev=${pr.deviationM}")
    }

    @Test
    fun `slice across the elbow keeps the corner vertex`() {
        val s =
            ShapeProjection.slice(
                lShape,
                lShape.cumulative[1] - 100.0,
                lShape.cumulative[1] + 100.0,
            )
        assertEquals(3, s.size)
        assertEquals(elbow, s[1])
    }

    // --- degenerate polylines ---

    @Test
    fun `duplicate-point polyline has zero length and a safe pointAt`() {
        val degenerate = Polyline(listOf(Point(51.0, 17.0), Point(51.0, 17.0)))
        assertEquals(0.0, degenerate.lengthM)
        assertEquals(Point(51.0, 17.0), degenerate.pointAt(0.0))
        assertEquals(Point(51.0, 17.0), degenerate.pointAt(500.0))
        assertEquals(Point(51.0, 17.0), degenerate.pointAt(-500.0))
    }

    @Test
    fun `projecting onto a duplicate-point polyline reports distance zero`() {
        val degenerate = Polyline(listOf(Point(51.0, 17.0), Point(51.0, 17.0)))
        val pr = degenerate.project(Point(51.01, 17.0))
        assertEquals(0.0, pr.distanceAlong)
        assertTrue(pr.deviationM > 0.0, "dev=${pr.deviationM}")
    }

    @Test
    fun `slicing a duplicate-point polyline yields two coincident points`() {
        val degenerate = Polyline(listOf(Point(51.0, 17.0), Point(51.0, 17.0)))
        val s = ShapeProjection.slice(degenerate, 0.0, 100.0)
        assertEquals(listOf(Point(51.0, 17.0), Point(51.0, 17.0)), s)
    }
}
