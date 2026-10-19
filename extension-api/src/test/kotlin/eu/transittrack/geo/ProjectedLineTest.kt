package eu.transittrack.geo

import org.locationtech.jts.geom.Coordinate
import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo

class ProjectedLineTest {
    private val line = ProjectedLine(
        listOf(Coordinate(17.0, 51.00), Coordinate(17.0, 51.01)),
    )

    @Test
    fun `lengthM matches the geodetic length`() {
        assertThat(line.lengthM).isCloseTo(1112.0, 5.0)
    }

    @Test
    fun `project onto the line finds zero deviation and the right distance along`() {
        val pr = line.project(Coordinate(17.0, 51.005))
        assertThat(pr.deviationM).isCloseTo(0.0, 5.0)
        assertThat(pr.distanceAlong).isCloseTo(556.0, 10.0)
    }

    @Test
    fun `project beside the line finds the perpendicular deviation`() {
        val pr = line.project(Coordinate(17.01, 51.005))
        assertThat(pr.deviationM).isCloseTo(700.0, 15.0)
    }

    @Test
    fun `project past either end clamps to that endpoint`() {
        assertThat(line.project(Coordinate(17.0, 50.99)).distanceAlong).isCloseTo(0.0, 1e-6)
        assertThat(line.project(Coordinate(17.0, 51.02)).distanceAlong).isCloseTo(line.lengthM, 1e-6)
    }

    @Test
    fun `pointAt interpolates back to a coordinate close to the original`() {
        val mid = line.pointAt(556.0)
        assertThat(mid.y).isCloseTo(51.005, 1e-3)
        assertThat(mid.x).isCloseTo(17.0, 1e-3)
    }

    @Test
    fun `a single-coordinate line has zero length and never builds a LengthIndexedLine`() {
        val single = ProjectedLine(listOf(Coordinate(17.0, 51.0)))
        assertThat(single.lengthM).isCloseTo(0.0, 1e-9)
        assertThat(single.pointAt(0.0)).isEqualTo(Coordinate(17.0, 51.0))
    }

    @Test
    fun `a line with duplicate consecutive coordinates does not throw`() {
        val degenerate = ProjectedLine(
            listOf(Coordinate(17.0, 51.0), Coordinate(17.0, 51.0), Coordinate(17.0, 51.01)),
        )
        assertThat(degenerate.lengthM).isCloseTo(1112.0, 5.0)
        assertThat(degenerate.project(Coordinate(17.0, 51.0)).deviationM).isCloseTo(0.0, 5.0)
    }

    @Test
    fun `project on a far vertex and past it stays in geodetic space, not raw planar distance`() {
        // Representative point is coordinates[size / 2] = the corner (index 1): on the central
        // meridian. The second leg runs east, away from that meridian, where the Transverse
        // Mercator projection's scale factor departs measurably from 1 — exactly the case where a
        // raw (unremapped) planar distanceAlong would disagree with the geodetic cumulative.
        val corner = Coordinate(17.0, 51.01)
        val farVertex = Coordinate(17.5, 51.01)
        val bend = ProjectedLine(listOf(Coordinate(17.0, 51.00), corner, farVertex))

        assertThat(bend.project(farVertex).distanceAlong).isCloseTo(bend.cumulative[2], 1e-3)

        val pastFarEnd = Coordinate(17.6, 51.01)
        assertThat(bend.project(pastFarEnd).distanceAlong).isCloseTo(bend.lengthM, 1e-3)
    }

    @Test
    fun `pointAt clamps negative and overshooting distances to the respective endpoint`() {
        val start = line.pointAt(-5.0)
        assertThat(start.y).isCloseTo(51.00, 1e-6)
        assertThat(start.x).isCloseTo(17.0, 1e-6)

        val end = line.pointAt(line.lengthM + 5.0)
        assertThat(end.y).isCloseTo(51.01, 1e-6)
        assertThat(end.x).isCloseTo(17.0, 1e-6)
    }
}
