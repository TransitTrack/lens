package eu.transittrack.avl

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.NestedConfigurationProperty

@ConfigurationProperties("transittrack.avl")
data class AvlProperties(
    val enabled: Boolean = false,
    @NestedConfigurationProperty val retention: Retention = Retention(),
    @NestedConfigurationProperty val match: Match = Match(),
) {
    data class Retention(
        val reportHours: Long = 24,
        val matchHours: Long = 72,
        val staleVehicleHours: Long = 12,
        val sweepCron: String = "0 0 * * * *",
    )

    data class Match(
        val backtrackToleranceM: Double = 30.0,
        val maxDeviationM: Double = 60.0,
        val unmatchAfterFailures: Int = 3,
        val tripEndAdvanceGraceSec: Int = 120,
        val candidateTimeSlackSec: Int = 1_800,
        val matchIntervalMs: Long = 5_000,
        val claimBatchSize: Int = 500,
        val reassignHysteresis: Double = 0.15,
        @NestedConfigurationProperty val scoreWeights: ScoreWeights = ScoreWeights(),
    )

    data class ScoreWeights(
        val deviation: Double = 0.4,
        val heading: Double = 0.2,
        val schedule: Double = 0.2,
        val continuity: Double = 0.2,
    )
}
