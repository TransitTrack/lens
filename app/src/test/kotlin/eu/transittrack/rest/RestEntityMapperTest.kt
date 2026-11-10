package eu.transittrack.rest

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

import eu.transittrack.gtfs.model.Trip
import eu.transittrack.gtfs.model.TripRepository
import eu.transittrack.schedule.model.TripPattern
import eu.transittrack.schedule.model.TripPatternRepository

class RestEntityMapperTest {
    private fun trip(
        tripId: String,
        patternId: Long?,
    ) = Trip(1, "route", "service", tripId, null, null, null, null, null, null, null, patternId)

    private fun pattern(
        id: Long,
        key: String,
    ) = TripPattern(1, key, "route", null, null, null, null, 2, null, null, 1).apply { this.id = id }

    @Test
    fun `trips expose their pattern key and resolve every pattern in one lookup`() {
        val patterns = mock<TripPatternRepository>()
        whenever(patterns.findAllById(listOf(7L))).thenReturn(listOf(pattern(7, "route:0:a")))
        val mapper = testEntityMapper(patterns = patterns)

        val mapped = mapper.trips(listOf(trip("t1", 7), trip("t2", 7), trip("t3", null)))

        assertEquals(listOf("route:0:a", "route:0:a", null), mapped.map { it.patternKey })
        assertNull(mapped.last().patternKey)
    }

    @Test
    fun `trips without a pattern need no lookup`() {
        val mapped = testEntityMapper(trips = mock<TripRepository>()).trips(listOf(trip("t1", null)))
        assertEquals("t1", mapped.single().tripId)
    }
}
