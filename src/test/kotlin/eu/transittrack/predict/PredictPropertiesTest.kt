package eu.transittrack.predict

import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource

import eu.transittrack.feed.FeedsProperties

class PredictPropertiesTest {
    private fun bind(map: Map<String, Any>): PredictProperties =
        Binder(MapConfigurationPropertySource(map))
            .bind("transittrack.predict", PredictProperties::class.java)
            .get()

    private fun bindFeeds(map: Map<String, Any>): FeedsProperties =
        Binder(MapConfigurationPropertySource(map))
            .bind("transittrack.feed", FeedsProperties::class.java)
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
        val p = bindFeeds(
            mapOf(
                "transittrack.feed.feeds[0].code" to "mbta",
                "transittrack.feed.feeds[0].name" to "MBTA",
                "transittrack.feed.feeds[0].url" to "https://example.test/gtfs.zip",
                "transittrack.feed.feeds[0].avl.url" to "https://example.test/vp.pb",
                "transittrack.feed.feeds[0].avl.prediction-algorithm" to "KALMAN",
                "transittrack.feed.feeds[0].avl.prediction-mode" to "EVALUATION",
            ),
        )
        val avl = p.feeds.single().avl!!
        assertThat(avl.predictionAlgorithm).isEqualTo(PredictionAlgorithm.KALMAN)
        assertThat(avl.predictionMode).isEqualTo(PredictionMode.EVALUATION)
    }
}
