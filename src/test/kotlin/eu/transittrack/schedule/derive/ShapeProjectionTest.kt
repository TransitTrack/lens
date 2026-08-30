package eu.transittrack.schedule.derive

import eu.transittrack.haversineMeters
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShapeProjectionTest {
    private val line = Polyline(
        listOf(Point(51.100, 17.000), Point(51.110, 17.000), Point(51.120, 17.000)),
    )

    @Test fun `cumulative distances are monotonic and start at zero`() {
        assertEquals(0.0, line.cumulative[0])
        assertTrue(line.cumulative[1] < line.cumulative[2])
        assertTrue(abs(line.lengthM - haversineMeters(51.100, 17.000, 51.120, 17.000)) < 5.0)
    }

    @Test fun `project a point near the middle vertex`() {
        val pr = line.project(Point(51.1101, 17.0005))
        assertTrue(abs(pr.distanceAlong - line.cumulative[1]) < 30.0, "along=${pr.distanceAlong}")
        assertTrue(pr.deviationM < 60.0, "dev=${pr.deviationM}")
    }

    @Test fun `project a far-off point reports large deviation`() {
        val pr = line.project(Point(51.110, 17.050))
        assertTrue(pr.deviationM > 1000.0)
    }

    @Test fun `pointAt endpoints`() {
        assertEquals(51.100, line.pointAt(0.0).lat, 1e-9)
        assertEquals(51.120, line.pointAt(line.lengthM).lat, 1e-6)
    }

    @Test fun `slice keeps intermediate vertices`() {
        val s = ShapeProjection.slice(line, 0.0, line.lengthM)
        assertEquals(3, s.size)
        assertEquals(51.110, s[1].lat, 1e-9)
    }

    @Test fun `slice with no vertex between yields two points`() {
        val s = ShapeProjection.slice(line, line.cumulative[1] + 10.0, line.cumulative[2] - 10.0)
        assertEquals(2, s.size)
    }

    @Test fun `straightLine is the two endpoints`() {
        val s = ShapeProjection.straightLine(Point(1.0, 2.0), Point(3.0, 4.0))
        assertEquals(listOf(Point(1.0, 2.0), Point(3.0, 4.0)), s)
    }

    @Test fun `single-point polyline has zero length`() {
        assertEquals(0.0, Polyline(listOf(Point(51.0, 17.0))).lengthM)
    }
}
