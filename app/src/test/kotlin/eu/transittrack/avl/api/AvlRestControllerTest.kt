package eu.transittrack.avl.api

import kotlin.test.Test

import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

import eu.transittrack.avl.read.AvlReadService
import eu.transittrack.rest.generated.model.LatLon
import eu.transittrack.rest.generated.model.TripSpeedSample
import eu.transittrack.rest.generated.model.Vehicle
import eu.transittrack.rest.generated.model.VehicleTrail

class AvlRestControllerTest {
    private fun vehicle(id: String) =
        Vehicle(id, "2026-01-01T00:00:00Z", LatLon(44.0, 26.0), matched = true, stale = false, tripId = "trip")

    @Test
    fun `lists vehicles with pagination and exposes related resources by id`() {
        val read = mock<AvlReadService>()
        whenever(read.vehicles("avl", false)).thenReturn(listOf(vehicle("bus"), vehicle("bus2")))
        val mvc = MockMvcBuilders.standaloneSetup(AvlRestController(read)).build()
        mvc
            .perform(get("/api/v1/feeds/avl/avl/vehicles").param("offset", "1").param("limit", "1"))
            .andExpect(status().isOk)
            .andExpect(header().string("X-Total-Count", "2"))
            .andExpect(jsonPath("$[0].vehicleId").value("bus2"))
            .andExpect(jsonPath("$[0].tripId").value("trip"))
            .andExpect(jsonPath("$[0].trip").doesNotExist())
    }

    @Test
    fun `unknown vehicle is not found`() {
        val read = mock<AvlReadService>()
        val mvc = MockMvcBuilders.standaloneSetup(AvlRestController(read)).build()
        mvc.perform(get("/api/v1/feeds/avl/avl/vehicles/nope")).andExpect(status().isNotFound)
    }

    @Test
    fun `serves the vehicle trail summary and rejects an out-of-range limit`() {
        val read = mock<AvlReadService>()
        whenever(read.vehicleTrail("avl", "bus", 200)).thenReturn(
            VehicleTrail(
                reportCount = 2,
                track = listOf(LatLon(44.0, 26.0), LatLon(44.1, 26.1)),
                speeds = listOf(TripSpeedSample(distanceAlongTripM = 120.0, speedMps = 7.5)),
                avgReportIntervalSec = 15.0,
            ),
        )
        val mvc = MockMvcBuilders.standaloneSetup(AvlRestController(read)).build()

        mvc
            .perform(get("/api/v1/feeds/avl/avl/vehicles/bus/trail"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.reportCount").value(2))
            .andExpect(jsonPath("$.track[1].lat").value(44.1))
            .andExpect(jsonPath("$.speeds[0].distanceAlongTripM").value(120.0))
            .andExpect(jsonPath("$.avgReportIntervalSec").value(15.0))
        mvc.perform(get("/api/v1/feeds/avl/avl/vehicles/bus/trail").param("limit", "0")).andExpect(status().isBadRequest)
        mvc.perform(get("/api/v1/feeds/avl/avl/vehicles/bus/trail").param("limit", "1001")).andExpect(status().isBadRequest)
    }
}
