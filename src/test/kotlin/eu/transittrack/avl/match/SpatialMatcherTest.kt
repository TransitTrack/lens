package eu.transittrack.avl.match

import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.isBetween
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull

import eu.transittrack.Point
import eu.transittrack.Polyline
import eu.transittrack.avl.AvlProperties

class SpatialMatcherTest {
    private val matcher = SpatialMatcher(AvlProperties())

    // N-S line so the maths is simple: ~1113 m per 0.01 deg latitude.
    private val line = Polyline(listOf(Point(0.0, 0.0), Point(0.01, 0.0), Point(0.02, 0.0)))
    private val geom = PatternGeometry(line, doubleArrayOf(0.0, line.cumulative[1]), booleanArrayOf(false, false))

    @Test
    fun `projects onto the line, computes stop path index and heading`() {
        val m = matcher.match(geom, Point(0.015, 0.00001), minAlongM = null)
        assertThat(m).isNotNull()
        assertThat(m!!.stopPathIndex).isEqualTo(1)
        assertThat(m.distanceAlongTripM).isBetween(line.cumulative[1] - 5.0, line.cumulative[2] + 5.0)
        assertThat(m.heading!!).isBetween(-1.0, 1.0) // due north ~ 0 deg
    }

    @Test
    fun `rejects a point beyond maxDeviationM`() {
        assertThat(matcher.match(geom, Point(0.01, 0.01), minAlongM = null)).isNull()
    }

    @Test
    fun `rejects a backward jump past the tolerance`() {
        assertThat(matcher.match(geom, Point(0.001, 0.0), minAlongM = 1000.0)).isNull()
    }

    @Test
    fun `allows a small backward move within tolerance`() {
        val along0 = matcher.match(geom, Point(0.005, 0.0), minAlongM = null)!!.distanceAlongTripM
        assertThat(matcher.match(geom, Point(0.005, 0.0), minAlongM = along0 + 10.0)).isNotNull()
    }

    @Test
    fun `matches a layover stop well beyond maxDeviationM when within its allowable distance`() {
        // layoverStop[1]: last stop path (ends at (0.02, 0.0)) is a layover. Distance from the
        // previous stop (0.01, 0.0) is ~1113m, so allowable = max(1.5 x 1113, layoverDistanceM=2000)
        // = 2000m. This report is ~1113m east of the layover stop — well beyond maxDeviationM (60m)
        // from the shape but within the 2000m allowable layover radius.
        val layoverGeom = PatternGeometry(line, doubleArrayOf(0.0, line.cumulative[1]), booleanArrayOf(false, true))
        val m = matcher.match(layoverGeom, Point(0.02, 0.01), minAlongM = null)
        assertThat(m).isNotNull()
        assertThat(m!!.stopPathIndex).isEqualTo(1)
        assertThat(m.heading).isNull()
    }

    @Test
    fun `rejects a layover match beyond its allowable distance`() {
        val layoverGeom = PatternGeometry(line, doubleArrayOf(0.0, line.cumulative[1]), booleanArrayOf(false, true))
        assertThat(matcher.match(layoverGeom, Point(0.02, 0.05), minAlongM = null)).isNull()
    }

    @Test
    fun `first stop path layover is always allowed, no matter the distance`() {
        // Mirrors the reference: the first layover of a trip means deadheading in, so it's always a
        // valid match regardless of distance.
        val layoverGeom = PatternGeometry(line, doubleArrayOf(0.0, line.cumulative[1]), booleanArrayOf(true, false))
        val m = matcher.match(layoverGeom, Point(0.02, 0.05), minAlongM = null)
        assertThat(m).isNotNull()
        assertThat(m!!.stopPathIndex).isEqualTo(0)
    }

    @Test
    fun `a non-layover stop path gets no distance exemption`() {
        assertThat(matcher.match(geom, Point(0.02, 0.01), minAlongM = null)).isNull()
    }
}
