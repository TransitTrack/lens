package eu.transittrack.predict

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.NestedConfigurationProperty

@ConfigurationProperties("transittrack.predict")
data class PredictProperties(
    val enabled: Boolean = false,
    @NestedConfigurationProperty val run: Run = Run(),
    @NestedConfigurationProperty val learn: Learn = Learn(),
    @NestedConfigurationProperty val accuracy: Accuracy = Accuracy(),
    @NestedConfigurationProperty val retention: Retention = Retention(),
) {
    data class Run(
        val intervalMs: Long = 5_000,
        val claimBatchSize: Int = 500,
        /**
         * A stored prediction is rewritten only once its arrival or departure moves by at least this
         * many seconds (or it's [predictionRefreshSec] old): a vehicle reporting every few seconds
         * would otherwise rewrite its whole horizon, every algorithm, on every report.
         */
        val predictionMinChangeSec: Int = 10,
        /**
         * A stored prediction is rewritten at least this often, even unchanged. Readers only show a
         * prediction computed within the last 10 minutes, so this has to stay well below that.
         */
        val predictionRefreshSec: Int = 120,
    ) {
        init {
            require(predictionMinChangeSec >= 0) { "predictionMinChangeSec must be >= 0" }
            require(predictionRefreshSec in 0..<600) { "predictionRefreshSec must be in [0, 600)" }
        }
    }

    data class Learn(
        val maxPlausibleTravelTimeSec: Int = 1_800,
        val kalmanMeasurementNoiseSec2: Double = 400.0,
        val kalmanInitialVarianceSec2: Double = 3_600.0,
        /** Drift allowed per sample; keeps the Kalman gain from decaying to zero. */
        val kalmanProcessNoiseSec2: Double = 25.0,
        /** Past this many samples the historical average weighs each new sample as 1/window. */
        val historyWindow: Int = 50,
        /**
         * The schedule seed counts as this many samples in the historical average, so a single
         * observation blends with it instead of replacing it outright.
         */
        val historyPriorSamples: Int = 5,
        /**
         * A sample further than `max(outlierMinDeviationSec, outlierMaxRelativeDeviation * estimate)`
         * from the current estimate is clipped to that distance before either learner takes it in: one
         * bus held 20 minutes at a stop moves the estimate a little, not by minutes, while a lasting
         * change still gets through over a few samples.
         */
        val outlierMinDeviationSec: Double = 60.0,
        val outlierMaxRelativeDeviation: Double = 1.0,
        /** A time band (or a whole day type) is trusted over its coarser roll-up from this many samples. */
        val minBucketSamples: Int = 10,
    )

    data class Accuracy(
        /**
         * Lead-time horizons accuracy is sampled at, in seconds before the predicted arrival: per
         * stop and algorithm, the first prediction made within each horizon (and outside the next
         * shorter one) is kept and scored once the vehicle gets there.
         */
        val horizonsSec: List<Int> = listOf(60, 180, 300, 600, 900, 1_200, 1_800),
    )

    data class Retention(
        val predictionHours: Long = 6,
        val accuracyDays: Long = 30,
        val rawCrossingDays: Long = 90,
        val sweepCron: String = "0 15 * * * *",
    )
}
