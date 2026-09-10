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
        /**
         * Polling cycles with no usable match for a vehicle before it is considered unmatched
         * (`vehicle_state.matched = false`) and its trip/block assignment is dropped. Until this
         * many consecutive failed cycles, a vehicle that had a live match keeps that assignment,
         * flagged `stale`, so a brief GPS/descriptor outage doesn't drop it off its trip.
         */
        val unmatchAfterFailures: Int = 5,
        /**
         * "Went silent" handling for a matched vehicle that stops sending reports (so the failure
         * counter above never advances). Thresholds are `feed.pollIntervalSec` times the cycle
         * count, floored at [silentUnmatchMinSec]. At [silentStaleCycles] the vehicle is flagged
         * `stale` but keeps its trip/block assignment; at [silentUnmatchCycles] the assignment is
         * dropped and `matched` goes false — the same end-state as [unmatchAfterFailures].
         */
        val silentStaleCycles: Int = 3,
        val silentUnmatchCycles: Int = 5,
        val silentUnmatchMinSec: Int = 60,
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
