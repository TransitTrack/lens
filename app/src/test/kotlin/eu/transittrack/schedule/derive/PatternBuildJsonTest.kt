package eu.transittrack.schedule.derive

import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.isEqualTo

import eu.transittrack.ProjectJson
import eu.transittrack.geo.Point

class PatternBuildJsonTest {
    @Test
    fun `geometry is stored as ordered longitude latitude numeric arrays`() {
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
                json = ProjectJson,
            )

        assertThat(ProjectJson.decodeFromString<List<List<Double>>>(result.paths[0].pathGeometry!!))
            .isEqualTo(listOf(listOf(first.lon, first.lat)))
        assertThat(ProjectJson.decodeFromString<List<List<Double>>>(result.paths[1].pathGeometry!!))
            .isEqualTo(listOf(listOf(first.lon, first.lat), listOf(second.lon, second.lat)))
    }
}
