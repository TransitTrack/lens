package eu.transittrack.avl

import java.time.Duration
import java.time.temporal.ChronoUnit

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.NestedConfigurationProperty
import org.springframework.boot.convert.DurationUnit

@ConfigurationProperties("transittrack.avl")
data class AvlProperties(
    val enabled: Boolean = false,
    @NestedConfigurationProperty val ingest: Ingest = Ingest(),
    @NestedConfigurationProperty val retention: Retention = Retention(),
    @NestedConfigurationProperty val match: Match = Match(),
) {
    data class Ingest(
        /**
         * A decoded report older than this is dropped before dedup/insert rather than processed —
         * guards against a feed occasionally emitting stale/bogus timestamps (observed on the
         * `wroclaw` feed) that would otherwise get matched against the wrong service date/trip.
         */
        @DurationUnit(ChronoUnit.HOURS)
        val maxReportAgeHours: Duration = Duration.ofMinutes(10),
    )

    data class Retention(
        val reportDuration: Duration = Duration.ofHours(24),
        val matchDuration: Duration = Duration.ofHours(72),
        val staleVehicleDuration: Duration = Duration.ofHours(3),
        val sweepCron: String = "0 0 * * * *",
    )

    data class Match(
        val backtrackToleranceM: Double = 30.0,
        val maxDeviationM: Double = 60.0,
        /**
         * Layover stop paths are exempt from [maxDeviationM] — a vehicle is allowed to be off-route
         * during a scheduled layover (e.g. parked at a depot). The exemption isn't unconditional
         * though: the vehicle must be within `max(1.5x the distance from the previous stop, this)`
         * of the layover stop, mirroring transitclock's `SpatialMatcher.withinAllowableDistanceOfLayover`.
         */
        val layoverDistanceM: Double = 2_000.0,
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
