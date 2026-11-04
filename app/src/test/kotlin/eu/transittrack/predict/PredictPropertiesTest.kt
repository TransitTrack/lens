package eu.transittrack.predict

import kotlin.test.Test

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource

import eu.transittrack.FeedsProperties

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
        assertThat(p.learn.historyPriorSamples).isEqualTo(5)
        assertThat(p.learn.outlierMinDeviationSec).isEqualTo(60.0)
        assertThat(p.learn.outlierMaxRelativeDeviation).isEqualTo(1.0)
        assertThat(p.accuracy.horizonsSec).isEqualTo(listOf(60, 180, 300, 600, 900, 1_200, 1_800))
    }

    @Test
    fun `binds accuracy horizons`() {
        val p =
            bind(
                mapOf(
                    "transittrack.predict.accuracy.horizons-sec[0]" to "120",
                    "transittrack.predict.accuracy.horizons-sec[1]" to "600",
                ),
            )
        assertThat(p.accuracy.horizonsSec).isEqualTo(listOf(120, 600))
    }

    @Test
    fun `binds prediction algorithms on a feed`() {
        val p = bindFeeds(
            mapOf(
                "transittrack.feed.feeds[0].code" to "mbta",
                "transittrack.feed.feeds[0].name" to "MBTA",
                "transittrack.feed.feeds[0].url" to "https://example.test/gtfs.zip",
                "transittrack.feed.feeds[0].avl.url" to "https://example.test/vp.pb",
                "transittrack.feed.feeds[0].avl.prediction-algorithms[0]" to "SCHEDULE_ADHERENCE",
                "transittrack.feed.feeds[0].avl.prediction-algorithms[1]" to "KALMAN",
            ),
        )
        val avl = p.feeds.single().avl!!
        assertThat(avl.predictionAlgorithms).isEqualTo(listOf(PredictionAlgorithm.SCHEDULE_ADHERENCE, PredictionAlgorithm.KALMAN))
    }

    @Test
    fun `smileGbmIsLastAndBindsFromConfig`() {
        assertThat(PredictionAlgorithm.entries).isEqualTo(
            listOf(
                PredictionAlgorithm.SCHEDULE_ADHERENCE,
                PredictionAlgorithm.HISTORICAL_AVERAGE,
                PredictionAlgorithm.KALMAN,
                PredictionAlgorithm.SMILE_GBM,
            ),
        )
        val p = bindFeeds(
            mapOf(
                "transittrack.feed.feeds[0].code" to "mbta",
                "transittrack.feed.feeds[0].name" to "MBTA",
                "transittrack.feed.feeds[0].url" to "https://example.test/gtfs.zip",
                "transittrack.feed.feeds[0].avl.url" to "https://example.test/vp.pb",
                "transittrack.feed.feeds[0].avl.prediction-algorithms[0]" to "smile_gbm",
            ),
        )
        assertThat(
            p.feeds
                .single()
                .avl!!
                .predictionAlgorithms,
        ).isEqualTo(listOf(PredictionAlgorithm.SMILE_GBM))
    }

    @Test
    fun `smile defaults are valid`() {
        val p = PredictProperties()
        assertThat(p.smile.trees).isEqualTo(200)
        assertThat(p.late.scheduleToleranceSec).isEqualTo(300)
        assertThat(p.late.headwayFraction).isEqualTo(0.5)
    }

    @Test
    fun `smile rejects inverted quantiles`() {
        assertFailure { PredictProperties.Smile(lowQuantile = 0.9, highQuantile = 0.1) }
            .isInstanceOf(IllegalArgumentException::class)
    }

    @Test
    fun `smile max training rows defaults to 100k`() {
        assertThat(PredictProperties().smile.maxTrainingRows).isEqualTo(100_000)
    }

    @Test
    fun `late rejects non positive headway fraction`() {
        assertFailure { PredictProperties.Late(headwayFraction = 0.0) }
            .isInstanceOf(IllegalArgumentException::class)
    }

    @Test
    fun `training days cannot exceed raw crossing retention`() {
        assertFailure {
            PredictProperties(
                smile = PredictProperties.Smile(trainingDays = 91),
                retention = PredictProperties.Retention(rawCrossingDays = 90),
            )
        }.isInstanceOf(IllegalArgumentException::class)
    }
}
