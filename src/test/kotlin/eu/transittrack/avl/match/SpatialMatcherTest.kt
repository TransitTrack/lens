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

    // Three stop paths: the first stop (0.0, 0.0), a mid-pattern stop (0.01, 0.0) and the terminus (0.02, 0.0).
    private fun threeStops(vararg layover: Boolean) = PatternGeometry(line, doubleArrayOf(0.0, 0.0, line.cumulative[1]), layover)

    @Test
    fun `matches a layover stop well beyond maxDeviationM when within its allowable distance`() {
        // layoverStop[1]: the mid-pattern stop (0.01, 0.0) is a layover. Distance from the previous
        // stop (0.0, 0.0) is ~1113m, so allowable = max(1.5 x 1113, layoverDistanceM=50) = 1670m.
        // This report is ~1113m east of the layover stop — well beyond maxDeviationM (60m) from the
        // shape but within the 1670m allowable layover radius.
        val m = matcher.match(threeStops(false, true, false), Point(0.01, 0.01), minAlongM = null)
        assertThat(m).isNotNull()
        assertThat(m!!.stopPathIndex).isEqualTo(1)
        assertThat(m.heading).isNull()
    }

    @Test
    fun `rejects a layover match beyond its allowable distance`() {
        assertThat(matcher.match(threeStops(false, true, false), Point(0.01, 0.05), minAlongM = null)).isNull()
    }

    @Test
    fun `last stop path layover rejects a vehicle parked beyond layoverDistanceM`() {
        // The terminus is ~1113m past the previous stop, so 1.5x that gap would allow ~1670m. A
        // vehicle arriving drives the shape, though: ~1113m off it near the terminus it's parked
        // (e.g. in a depot), not at the terminus, so only layoverDistanceM applies.
        assertThat(matcher.match(threeStops(false, false, true), Point(0.02, 0.01), minAlongM = null)).isNull()
    }

    @Test
    fun `last stop path layover allows a vehicle within layoverDistanceM`() {
        // ~334m east of the terminus, layoverDistanceM raised to 500m.
        val wideLayover = SpatialMatcher(AvlProperties(match = AvlProperties.Match(layoverDistanceM = 500.0)))
        val m = wideLayover.match(threeStops(false, false, true), Point(0.02, 0.003), minAlongM = null)
        assertThat(m).isNotNull()
        assertThat(m!!.stopPathIndex).isEqualTo(2)
    }

    @Test
    fun `first stop path layover allows deadheading in within layoverDistanceM`() {
        // layoverStop[0]: first stop path (ends at (0.01, 0.0)) is a layover - deadheading in, with
        // no previous stop to measure from, so allowable = layoverDistanceM (raised to 500m here).
        // This report is ~334m east of it - well beyond maxDeviationM (60m) from the shape but within that radius.
        val wideLayover = SpatialMatcher(AvlProperties(match = AvlProperties.Match(layoverDistanceM = 500.0)))
        val layoverGeom = PatternGeometry(line, doubleArrayOf(0.0, line.cumulative[1]), booleanArrayOf(true, false))
        val m = wideLayover.match(layoverGeom, Point(0.01, 0.003), minAlongM = null)
        assertThat(m).isNotNull()
        assertThat(m!!.stopPathIndex).isEqualTo(0)
    }

    @Test
    fun `first stop path layover rejects a vehicle parked beyond layoverDistanceM`() {
        // ~111m east of the first stop: past maxDeviationM (60m) and the default layoverDistanceM
        // (50m) - a vehicle parked off-route near a trip's start (e.g. in a depot) isn't at its first stop.
        val layoverGeom = PatternGeometry(line, doubleArrayOf(0.0, line.cumulative[1]), booleanArrayOf(true, false))
        assertThat(matcher.match(layoverGeom, Point(0.01, 0.001), minAlongM = null)).isNull()
    }

    @Test
    fun `a non-layover stop path gets no distance exemption`() {
        assertThat(matcher.match(geom, Point(0.02, 0.01), minAlongM = null)).isNull()
    }
}
