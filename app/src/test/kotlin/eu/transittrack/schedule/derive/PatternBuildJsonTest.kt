package eu.transittrack.schedule.derive

import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.isEqualTo

import eu.transittrack.geo.Point
import eu.transittrack.geo.Position

class PatternBuildJsonTest {
    @Test
    fun `pattern geometry concatenates the stop segments in order with full precision`() {
        val first = Point(51.98765432109876, 17.123456789012345)
        val second = Point(52.0, 18.0)
        val result =
            buildPattern(
                revisionId = 1,
                routeId = "R",
                shapeId = null,
                directionId = null,
                headsign = null,
                rows =
                    listOf("A", "B").mapIndexed { index, stop ->
                        CleanStopTime(stop, index, 3600, 3600, null, null, null, null, false)
                    },
                stopIds = listOf("A", "B"),
                stopCoord = mapOf("A" to first, "B" to second),
                line = null,
                maxDeviationM = 100.0,
            )

        // The shared boundary point of the two segments appears once.
        assertThat(result.pattern.geometry!!.coordinates)
            .isEqualTo(listOf(Position(first.lon, first.lat), Position(second.lon, second.lat)))
    }
}
