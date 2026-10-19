package eu.transittrack.geo

import org.locationtech.jts.geom.Coordinate
import org.geotools.referencing.GeodeticCalculator
import org.geotools.referencing.datum.DefaultEllipsoid
import kotlin.test.Test
import assertk.assertThat
import assertk.assertions.isCloseTo

class GeoToolsSmokeTest {
    @Test
    fun `GeodeticCalculator computes a known distance on Java 25`() {
        val calc = GeodeticCalculator(DefaultEllipsoid.WGS84)
        calc.setStartingGeographicPoint(17.0, 51.0)
        calc.setDestinationGeographicPoint(17.0, 51.01)
        assertThat(calc.orthodromicDistance).isCloseTo(1112.0, 5.0)
    }
}
