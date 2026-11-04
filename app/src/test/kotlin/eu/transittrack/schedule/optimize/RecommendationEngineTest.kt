package eu.transittrack.schedule.optimize

import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull

class RecommendationEngineTest {
    @Test
    fun `proposes a material stop-time adjustment from enough robust observations`() {
        val result = RecommendationEngine.stopTime(300, listOf(360.0, 370.0, 9_999.0), minimumSamples = 2, materialitySec = 30)

        assertThat(result).isEqualTo(RecommendationCandidate.StopTime(observedSec = 365, deltaSec = 65, sampleCount = 2))
    }

    @Test
    fun `does not propose a nonmaterial or undersampled stop-time adjustment`() {
        assertThat(RecommendationEngine.stopTime(300, listOf(320.0), minimumSamples = 2, materialitySec = 30)).isNull()
        assertThat(RecommendationEngine.stopTime(300, listOf(320.0, 320.0), minimumSamples = 2, materialitySec = 30)).isNull()
    }

    @Test
    fun `proposes an order-preserving trip shift that improves adjacent headways`() {
        val result = RecommendationEngine.tripShift(
            tripId = "t2", scheduledStartSec = 3_700, observedStartSec = listOf(3_600.0, 3_610.0),
            predecessorStartSec = 3_300, successorStartSec = 3_900, targetHeadwaySec = 300,
            minimumSamples = 2, materialitySec = 30,
        )

        assertThat(result).isEqualTo(RecommendationCandidate.TripShift("t2", 3_605, -95, 2))
    }

    @Test
    fun `rejects a trip shift that would cross an adjacent trip`() {
        val result = RecommendationEngine.tripShift(
            tripId = "t2", scheduledStartSec = 3_700, observedStartSec = listOf(3_250.0, 3_260.0),
            predecessorStartSec = 3_300, successorStartSec = 3_900, targetHeadwaySec = 300,
            minimumSamples = 2, materialitySec = 30,
        )

        assertThat(result).isNull()
    }
}
