package eu.transittrack.avl.read

import java.time.Instant
import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

import eu.transittrack.avl.model.VehicleState
import eu.transittrack.gtfs.model.Route
import eu.transittrack.gtfs.model.RouteRepository
import eu.transittrack.gtfs.model.Trip
import eu.transittrack.gtfs.model.TripRepository
import eu.transittrack.schedule.model.Block
import eu.transittrack.schedule.model.BlockRepository
import eu.transittrack.schedule.model.TripPattern
import eu.transittrack.schedule.model.TripPatternRepository

class MapVehicleReadServiceTest {
    private val read = mock<AvlReadService>()
    private val trips = mock<TripRepository>()
    private val routes = mock<RouteRepository>()
    private val blocks = mock<BlockRepository>()
    private val patterns = mock<TripPatternRepository>()
    private val service = MapVehicleReadService(read, trips, routes, blocks, patterns)

    @Test
    fun `joins trip route block and pattern for the whole fleet with one read per kind`() {
        // Three vehicles on two trips of one route; one has no match, one is not located in a revision.
        whenever(read.vehicleStates("f", false)).thenReturn(
            listOf(
                state("v1", revision = 7, trip = 11, block = 21, pattern = 31),
                state("v2", revision = 7, trip = 12, block = 21, pattern = 31),
                state("v3", revision = null, trip = 11, block = 21, pattern = 31),
                state("v4", revision = 7, trip = null, block = null, pattern = null),
            ),
        )
        whenever(trips.findAllById(listOf(11L, 12L))).thenReturn(listOf(trip(11, "T1"), trip(12, "T2")))
        whenever(routes.findByRouteIds(7, listOf("RA"))).thenReturn(listOf(route("RA", "A")))
        whenever(blocks.findAllById(listOf(21L))).thenReturn(listOf(block(21, "B1")))
        whenever(patterns.findAllById(listOf(31L))).thenReturn(listOf(pattern(31, 250.0)))

        val vehicles = service.vehicles("f", false)

        assertThat(vehicles.map { it.vehicleId }).containsExactly("v1", "v2", "v3", "v4")
        val v1 = vehicles[0]
        assertThat(v1.trip?.tripId).isEqualTo("T1")
        assertThat(v1.trip?.route?.routeShortName).isEqualTo("A")
        assertThat(v1.block?.blockId).isEqualTo("B1")
        assertThat(v1.pattern?.lengthM).isEqualTo(250.0)
        assertThat(vehicles[1].trip?.tripId).isEqualTo("T2")
        // Without a revision the ids mean nothing: no trip, block or pattern is attached.
        assertThat(vehicles[2].trip).isNull()
        assertThat(vehicles[2].block).isNull()
        assertThat(vehicles[3].trip).isNull()

        verify(trips, times(1)).findAllById(any())
        verify(routes, times(1)).findByRouteIds(any(), any())
        verify(blocks, times(1)).findAllById(any())
        verify(patterns, times(1)).findAllById(any())
        verify(trips, never()).findById(any())
    }

    private fun state(
        id: String,
        revision: Long?,
        trip: Long?,
        block: Long?,
        pattern: Long?,
    ) = VehicleState(
        feedCode = "f", vehicleId = id, vehicleLabel = null, reportTs = Instant.parse("2026-01-01T00:00:00Z"),
        lat = 44.0, lon = 26.0, bearing = null, speedMps = null, occupancyStatus = null, matched = trip != null,
        stale = false, consecutiveFailures = 0, revisionId = revision, tripRowId = trip, blockPk = block,
        tripPatternId = pattern, stopPathIndex = null, distanceAlongTripM = null, scheduleAdherenceSec = null,
        snappedLat = null, snappedLon = null, updatedAt = Instant.parse("2026-01-01T00:00:00Z"),
    )

    private fun trip(
        rowId: Long,
        tripId: String,
    ) = Trip(
        revisionId = 7, routeId = "RA", serviceId = "WK", tripId = tripId, tripHeadsign = "H", tripShortName = null,
        directionId = 0, blockId = null, shapeId = null, wheelchairAccessible = null, bikesAllowed = null,
    ).also { it.id = rowId }

    private fun route(
        id: String,
        shortName: String,
    ) = Route(
        revisionId = 7, routeId = id, agencyId = null, routeShortName = shortName, routeLongName = null,
        routeDesc = null, routeType = 3, routeUrl = null, routeColor = null, routeTextColor = null,
        routeSortOrder = null, continuousPickup = null, continuousDropOff = null, networkId = null,
    )

    private fun block(
        rowId: Long,
        blockId: String,
    ) = Block(7, blockId, "WK", 0, 100, 2, listOf("RA")).also { it.id = rowId }

    private fun pattern(
        rowId: Long,
        lengthM: Double,
    ) = TripPattern(
        revisionId = 7, patternKey = "k", routeId = "RA", routeShortName = null, directionId = null, headsign = null,
        shapeId = null, stopCount = 3, lengthM = lengthM, tripCount = 1,
    ).also { it.id = rowId }
}
