package eu.transittrack.avl.match.cache

import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

import eu.transittrack.geo.Line
import eu.transittrack.geo.Position
import eu.transittrack.schedule.model.StopPath
import eu.transittrack.schedule.model.TripPattern
import eu.transittrack.schedule.model.TripPatternRepository

class CachedPatternGeometryJsonTest {
    private val stopPaths = mock<CachedStopPathReader>()
    private val patterns = mock<TripPatternRepository>()
    private val reader = CachedPatternGeometryReader(stopPaths, patterns)

    @Test
    fun `pattern line is used as stored and cumulative distances come from stop path lengths`() {
        val line =
            Line(
                listOf(
                    Position(17.123456789012345, 51.98765432109876),
                    Position(18.0, 52.0),
                    Position(17.123456789012345, 51.98765432109876),
                ),
            )
        whenever(stopPaths.orderedByPattern(1, 2)).thenReturn(
            listOf(path(0, 0.0, true), path(1, 125.5), path(2, 125.5, true)),
        )
        whenever(patterns.findById(2)).thenReturn(pattern(line))

        val geometry = reader.geometry(1, 2)!!

        assertThat(geometry.line).isEqualTo(line)
        assertThat(geometry.stopPathCumM.toList()).isEqualTo(listOf(0.0, 0.0, 125.5))
        assertThat(geometry.layoverStop.toList()).isEqualTo(listOf(true, false, true))
    }

    @Test
    fun `a pattern without stored geometry has no pattern geometry`() {
        whenever(stopPaths.orderedByPattern(1, 2)).thenReturn(listOf(path(0, 0.0, true)))
        whenever(patterns.findById(2)).thenReturn(pattern(null))

        assertThat(reader.geometry(1, 2)).isNull()
    }

    private fun pattern(geometry: Line?) =
        TripPattern(
            revisionId = 1,
            patternKey = "k",
            routeId = "R",
            routeShortName = null,
            directionId = null,
            headsign = null,
            shapeId = null,
            stopCount = 3,
            lengthM = 251.0,
            tripCount = 1,
            geometry = geometry,
        )

    private fun path(
        index: Int,
        length: Double,
        layover: Boolean = false,
    ) = StopPath(
        revisionId = 1,
        tripPatternId = 2,
        stopPathIndex = index,
        stopId = "S$index",
        routeId = "R",
        stopSeq = index,
        lengthM = length,
        pickupType = null,
        dropOffType = null,
        waitStop = false,
        scheduleAdherenceStop = false,
        layoverStop = layover,
        breakTimeSec = null,
    )
}
