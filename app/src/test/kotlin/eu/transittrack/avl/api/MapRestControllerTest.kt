package eu.transittrack.avl.api

import kotlin.test.Test

import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

import eu.transittrack.avl.model.VehicleState
import eu.transittrack.avl.read.MapVehicle
import eu.transittrack.avl.read.MapVehicleReadService
import eu.transittrack.rest.generated.model.LatLon
import eu.transittrack.schedule.read.FeedGeometryService

class MapRestControllerTest {
    @Test
    fun `serves network geometry as compressed bytes`() {
        val geometry = mock<FeedGeometryService>()
        whenever(geometry.feedGeometry("test", null)).thenReturn("H4sIAAAAAAAA/wMAAAAAAAAAAAA=")
        val mvc = MockMvcBuilders
            .standaloneSetup(MapRestController(mock<MapVehicleReadService>(), geometry))
            .build()
        mvc
            .perform(get("/api/v1/feeds/test/geometry"))
            .andExpect(status().isOk)
            .andExpect(content().contentType("application/geo+json"))
            .andExpect(header().string("Content-Encoding", "gzip"))
            .andExpect(
                content().bytes(
                    java.util.Base64
                        .getDecoder()
                        .decode("H4sIAAAAAAAA/wMAAAAAAAAAAAA="),
                ),
            )
    }

    @Test
    fun `vehicles response contains only map fields`() {
        val vehicles = mock<MapVehicleReadService>()
        whenever(vehicles.vehicles("test", false)).thenReturn(
            listOf(
                MapVehicle(
                    "v1", null, "2026-01-01T00:00:00Z", LatLon(44.0, 26.0), null, false, false,
                    null, null, null, null, null, null, null,
                ),
            ),
        )
        val mvc = MockMvcBuilders.standaloneSetup(MapRestController(vehicles, mock<FeedGeometryService>())).build()
        mvc
            .perform(get("/api/v1/feeds/test/vehicles"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].vehicleId").value("v1"))
            .andExpect(jsonPath("$[0].position.lat").value(44.0))
            .andExpect(jsonPath("$[0].gtfsFeedCode").doesNotExist())
    }
}
