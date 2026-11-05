package eu.transittrack.avl.match.cache

import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

import eu.transittrack.ProjectJson
import eu.transittrack.geo.Position
import eu.transittrack.schedule.model.StopPath

class CachedPatternGeometryJsonTest {
    @Test
    fun `stored geometry keeps precision coordinate order and revisited points`() {
        val stopPaths = mock<CachedStopPathReader>()
        whenever(stopPaths.orderedByPattern(1, 2)).thenReturn(
            listOf(
                path(0, 0.0, "[[17.123456789012345,51.98765432109876]]", true),
                path(1, 125.5, "[[17.123456789012345,51.98765432109876],[18,52]]"),
                path(2, 125.5, "[[18.0,52.0],[17.123456789012345,51.98765432109876]]", true),
            ),
        )

        val geometry = CachedPatternGeometryReader(stopPaths, ProjectJson).geometry(1, 2)!!

        assertThat(geometry.line.coordinates).isEqualTo(
            listOf(
                Position(17.123456789012345, 51.98765432109876),
                Position(18.0, 52.0),
                Position(17.123456789012345, 51.98765432109876),
            ),
        )
        assertThat(geometry.stopPathCumM.toList()).isEqualTo(listOf(0.0, 0.0, 125.5))
        assertThat(geometry.layoverStop.toList()).isEqualTo(listOf(true, false, true))
    }

    private fun path(
        index: Int,
        length: Double,
        raw: String,
        layover: Boolean = false,
    ) = StopPath(
        revisionId = 1,
        tripPatternId = 2,
        stopPathIndex = index,
        stopId = "S$index",
        routeId = "R",
        stopSeq = index,
        lengthM = length,
        pathGeometry = raw,
        pickupType = null,
        dropOffType = null,
        waitStop = false,
        scheduleAdherenceStop = false,
        layoverStop = layover,
        breakTimeSec = null,
    )
}
