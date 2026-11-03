package eu.transittrack.predict

import java.time.Instant

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.NestedConfigurationProperty

@ConfigurationProperties("transittrack.predict")
data class PredictProperties(
    val enabled: Boolean = false,
    @NestedConfigurationProperty val run: Run = Run(),
    @NestedConfigurationProperty val learn: Learn = Learn(),
    @NestedConfigurationProperty val accuracy: Accuracy = Accuracy(),
    @NestedConfigurationProperty val retention: Retention = Retention(),
    @NestedConfigurationProperty val smile: Smile = Smile(),
    @NestedConfigurationProperty val late: Late = Late(),
) {
    init {
        require(smile.trainingDays <= retention.rawCrossingDays) {
            "smile.trainingDays (${smile.trainingDays}) must be <= retention.rawCrossingDays (${retention.rawCrossingDays})"
        }
    }

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
         * Refreshes unchanged rows with newer source evidence before [maxSourceAgeSec] expires.
         */
        val predictionRefreshSec: Int = 120,
        val maxSourceAgeSec: Int = 600,
    ) {
        init {
            require(intervalMs > 0) { "intervalMs must be > 0" }
            require(claimBatchSize > 0) { "claimBatchSize must be > 0" }
            require(predictionMinChangeSec >= 0) { "predictionMinChangeSec must be >= 0" }
            require(maxSourceAgeSec > 0) { "maxSourceAgeSec must be > 0" }
            require(predictionRefreshSec in 0..<maxSourceAgeSec) { "predictionRefreshSec must be in [0, maxSourceAgeSec)" }
        }

        fun isFreshSource(
            sourceReportTs: Instant,
            at: Instant,
        ): Boolean = !sourceReportTs.isBefore(at.minusSeconds(maxSourceAgeSec.toLong()))
    }

    data class Learn(
        val maxPlausibleTravelTimeSec: Int = 1_800,
        val kalmanMeasurementNoiseSec2: Double = 400.0,
        val kalmanInitialVarianceSec2: Double = 3_600.0,
        /** Drift allowed per sample; keeps the Kalman gain from decaying to zero. */
        val kalmanProcessNoiseSec2: Double = 25.0,
        val kalmanVarianceGrowthSec2PerHour: Double = 400.0,
        val maxReliableReportGapSec: Int = 120,
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
    ) {
        init {
            require(maxPlausibleTravelTimeSec > 0) { "maxPlausibleTravelTimeSec must be > 0" }
            require(maxReliableReportGapSec > 0) { "maxReliableReportGapSec must be > 0" }
            require(kalmanMeasurementNoiseSec2.isFinite() && kalmanMeasurementNoiseSec2 > 0) {
                "kalmanMeasurementNoiseSec2 must be finite and > 0"
            }
            require(kalmanInitialVarianceSec2.isFinite() && kalmanInitialVarianceSec2 > 0) {
                "kalmanInitialVarianceSec2 must be finite and > 0"
            }
            require(kalmanProcessNoiseSec2.isFinite() && kalmanProcessNoiseSec2 >= 0) { "kalmanProcessNoiseSec2 must be finite and >= 0" }
            require(kalmanVarianceGrowthSec2PerHour.isFinite() && kalmanVarianceGrowthSec2PerHour >= 0) {
                "kalmanVarianceGrowthSec2PerHour must be finite and >= 0"
            }
            require(historyWindow > 0) { "historyWindow must be > 0" }
            require(historyPriorSamples >= 0) { "historyPriorSamples must be >= 0" }
            require(outlierMinDeviationSec.isFinite() && outlierMinDeviationSec >= 0) { "outlierMinDeviationSec must be finite and >= 0" }
            require(outlierMaxRelativeDeviation.isFinite() && outlierMaxRelativeDeviation >= 0) {
                "outlierMaxRelativeDeviation must be finite and >= 0"
            }
            require(minBucketSamples > 0) { "minBucketSamples must be > 0" }
        }
    }

    data class Accuracy(
        /**
         * Upper bounds on actual lead time from forecast issuance to crossing. Shared snapshots are
         * captured independently of predicted ETAs; the earliest issued cohort in each bin is scored.
         */
        val horizonsSec: List<Int> = listOf(60, 180, 300, 600, 900, 1_200, 1_800),
        val sampleIntervalSec: Int = 60,
    ) {
        init {
            require(horizonsSec.isNotEmpty() && horizonsSec.all { it > 0 }) { "horizonsSec must contain positive horizons" }
            require(sampleIntervalSec > 0) { "sampleIntervalSec must be > 0" }
        }
    }

    data class Retention(
        val predictionHours: Long = 6,
        val accuracyDays: Long = 30,
        val rawCrossingDays: Long = 90,
        val sweepCron: String = "0 15 * * * *",
    )

    /** Gradient-boosted (SMILE_GBM) prediction model: training schedule, data window and hyperparameters. */
    data class Smile(
        /** Spring cron for the training job (default: daily 03:30). */
        val trainCron: String = "0 30 3 * * *",
        /** How often the serving side checks for a newer trained model. */
        val refreshMs: Long = 300_000,
        /** Training is skipped when fewer crossing rows than this are available. */
        val minTrainingRows: Int = 2_000,
        /** Training sample is capped to this many rows. */
        val maxTrainingRows: Int = 200_000,
        /** Days of raw crossings used for training; must not exceed retention.rawCrossingDays. */
        val trainingDays: Int = 60,
        val trees: Int = 200,
        val maxDepth: Int = 6,
        val shrinkage: Double = 0.1,
        val subsample: Double = 0.7,
        /** Lower prediction-interval quantile; 0 < low < 0.5. */
        val lowQuantile: Double = 0.1,
        /** Upper prediction-interval quantile; 0.5 < high < 1. */
        val highQuantile: Double = 0.9,
    ) {
        init {
            require(refreshMs > 0) { "refreshMs must be > 0" }
            require(minTrainingRows > 0) { "minTrainingRows must be > 0" }
            require(maxTrainingRows >= minTrainingRows) { "maxTrainingRows must be >= minTrainingRows" }
            require(trainingDays > 0) { "trainingDays must be > 0" }
            require(trees > 0) { "trees must be > 0" }
            require(maxDepth > 0) { "maxDepth must be > 0" }
            require(shrinkage.isFinite() && shrinkage > 0 && shrinkage <= 1) { "shrinkage must be in (0, 1]" }
            require(subsample.isFinite() && subsample > 0 && subsample <= 1) { "subsample must be in (0, 1]" }
            require(lowQuantile.isFinite() && lowQuantile > 0 && lowQuantile < 0.5) { "lowQuantile must be in (0, 0.5)" }
            require(highQuantile.isFinite() && highQuantile > 0.5 && highQuantile < 1) { "highQuantile must be in (0.5, 1)" }
        }
    }

    /** Lateness policy: when a vehicle counts as late relative to schedule or headway. */
    data class Late(
        /** Seconds past schedule tolerated before a trip is considered late. */
        val scheduleToleranceSec: Int = 300,
        /** For headway-based services: fraction of the headway tolerated. */
        val headwayFraction: Double = 0.5,
    ) {
        init {
            require(scheduleToleranceSec >= 0) { "scheduleToleranceSec must be >= 0" }
            require(headwayFraction.isFinite() && headwayFraction > 0) { "headwayFraction must be finite and > 0" }
        }
    }
}
