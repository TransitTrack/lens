package eu.transittrack.predict

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.NestedConfigurationProperty

enum class PredictionAlgorithm { SCHEDULE_ADHERENCE, HISTORICAL_AVERAGE, KALMAN }

enum class PredictionMode { SINGLE, EVALUATION }

@ConfigurationProperties("transittrack.predict")
data class PredictProperties(
    val enabled: Boolean = false,
    @NestedConfigurationProperty val run: Run = Run(),
    @NestedConfigurationProperty val learn: Learn = Learn(),
    @NestedConfigurationProperty val retention: Retention = Retention(),
) {
    data class Run(
        val intervalMs: Long = 5_000,
        val claimBatchSize: Int = 500,
    )

    data class Learn(
        val maxPlausibleTravelTimeSec: Int = 1_800,
        val kalmanMeasurementNoiseSec2: Double = 400.0,
        val kalmanInitialVarianceSec2: Double = 3_600.0,
    )

    data class Retention(
        val predictionHours: Long = 6,
        val accuracyDays: Long = 30,
        val rawCrossingDays: Long = 90,
        val sweepCron: String = "0 15 * * * *",
    )
}
