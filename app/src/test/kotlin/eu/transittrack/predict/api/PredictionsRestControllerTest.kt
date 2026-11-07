package eu.transittrack.predict.api

import kotlin.test.Test

import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

import eu.transittrack.predict.read.HeadwayReadService
import eu.transittrack.predict.read.PredictionReadService
import eu.transittrack.rest.generated.model.Headway
import eu.transittrack.rest.generated.model.StopPrediction

class PredictionsRestControllerTest {
    @Test
    fun `vehicle predictions are paginated and scoped to the vehicle`() {
        val read = mock<PredictionReadService>()
        val p = StopPrediction(stopPathIndex = 1, stopId = "s", predictedArrival = "2026-01-01T00:00:00Z")
        val second = p.copy(stopPathIndex = 2)
        whenever(read.vehiclePredictions("avl", "bus", null)).thenReturn(listOf(p, second))
        val mvc = MockMvcBuilders
            .standaloneSetup(
                PredictionsRestController(read, mock<HeadwayReadService>()),
            ).build()
        mvc
            .perform(get("/api/v1/avl-feeds/avl/vehicles/bus/predictions").param("offset", "1").param("limit", "1"))
            .andExpect(status().isOk)
            .andExpect(header().string("X-Total-Count", "2"))
            .andExpect(jsonPath("$[0].stopPathIndex").value(2))
            .andExpect(jsonPath("$[1]").doesNotExist())
    }

    @Test
    fun `headway passes route and direction to the service`() {
        val headways = mock<HeadwayReadService>()
        whenever(headways.headway("avl", "s", "r", 1, null)).thenReturn(Headway("s", "r", listOf(120), 1, 60, 180))
        val mvc = MockMvcBuilders
            .standaloneSetup(
                PredictionsRestController(mock<PredictionReadService>(), headways),
            ).build()
        mvc
            .perform(get("/api/v1/avl-feeds/avl/stops/s/headway").param("routeId", "r").param("directionId", "1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.waitSec").value(60))
            .andExpect(jsonPath("$.gapsSec[0]").value(120))
    }

    @Test
    fun `invalid accuracy window is rejected`() {
        val mvc = MockMvcBuilders
            .standaloneSetup(
                PredictionsRestController(mock<PredictionReadService>(), mock<HeadwayReadService>()),
            ).build()
        mvc
            .perform(get("/api/v1/avl-feeds/avl/prediction-accuracy").param("sinceDays", "0"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.status").value(400))
    }
}
