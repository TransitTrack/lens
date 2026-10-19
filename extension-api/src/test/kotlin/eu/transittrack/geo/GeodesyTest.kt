package eu.transittrack.geo

import org.locationtech.jts.geom.Coordinate
import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.isCloseTo
import assertk.assertions.isTrue

class GeodesyTest {
    private val south = Coordinate(17.0, 51.0)
    private val north = Coordinate(17.0, 51.01)

    @Test
    fun `geodeticDistanceM matches the known city-scale distance`() {
        assertThat(geodeticDistanceM(south, north)).isCloseTo(1112.0, 5.0)
    }

    @Test
    fun `geodeticDistanceM is symmetric and zero for identical points`() {
        assertThat(geodeticDistanceM(south, south)).isCloseTo(0.0, 1e-6)
        assertThat(geodeticDistanceM(south, north)).isCloseTo(geodeticDistanceM(north, south), 1e-6)
    }

    @Test
    fun `geodeticBearingDegrees north is 0, east is 90`() {
        assertThat(geodeticBearingDegrees(south, north)).isCloseTo(0.0, 1.0)
        assertThat(geodeticBearingDegrees(south, Coordinate(17.01, 51.0))).isCloseTo(90.0, 1.0)
    }

    @Test
    fun `geodeticBearingDegrees stays within 0,360`() {
        val b = geodeticBearingDegrees(south, Coordinate(16.99, 51.0))
        assertThat(b >= 0.0 && b < 360.0).isTrue()
    }
}
