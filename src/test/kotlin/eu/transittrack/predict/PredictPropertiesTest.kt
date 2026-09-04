package eu.transittrack.predict

import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource

import eu.transittrack.avl.AvlProperties

class PredictPropertiesTest {
    private fun bind(map: Map<String, Any>): PredictProperties =
        Binder(MapConfigurationPropertySource(map))
            .bind("transittrack.predict", PredictProperties::class.java)
            .get()

    private fun bindAvl(map: Map<String, Any>): AvlProperties =
        Binder(MapConfigurationPropertySource(map))
            .bind("transittrack.avl", AvlProperties::class.java)
            .get()

    @Test
    fun `defaults`() {
        val p = bind(mapOf("transittrack.predict.enabled" to "false"))
        assertThat(p.enabled).isFalse()
        assertThat(p.learn.maxPlausibleTravelTimeSec).isEqualTo(1_800)
        assertThat(p.learn.kalmanMeasurementNoiseSec2).isEqualTo(400.0)
        assertThat(p.retention.predictionHours).isEqualTo(6L)
    }

    @Test
    fun `binds prediction algorithm and mode on a feed`() {
        val p = bindAvl(
            mapOf(
                "transittrack.avl.feeds[0].code" to "mbta-vp",
                "transittrack.avl.feeds[0].name" to "MBTA VP",
                "transittrack.avl.feeds[0].gtfs-feed-code" to "mbta",
                "transittrack.avl.feeds[0].url" to "https://example.test/vp.pb",
                "transittrack.avl.feeds[0].prediction-algorithm" to "KALMAN",
                "transittrack.avl.feeds[0].prediction-mode" to "EVALUATION",
            ),
        )
        val f = p.feeds.single()
        assertThat(f.predictionAlgorithm).isEqualTo(PredictionAlgorithm.KALMAN)
        assertThat(f.predictionMode).isEqualTo(PredictionMode.EVALUATION)
    }
}
