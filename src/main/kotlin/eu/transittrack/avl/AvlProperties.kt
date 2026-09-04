package eu.transittrack.avl

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.NestedConfigurationProperty

enum class AvlFormat { GTFS_RT, STPT }

enum class AvlAssignmentMode { TRUST_DESCRIPTOR, DESCRIPTOR_THEN_INFER, FULL_INFERENCE }

@ConfigurationProperties("transittrack.avl")
data class AvlProperties(
    val enabled: Boolean = false,
    val pruneConfigFeeds: Boolean = false,
    @NestedConfigurationProperty val http: Http = Http(),
    @NestedConfigurationProperty val retention: Retention = Retention(),
    @NestedConfigurationProperty val match: Match = Match(),
    val feeds: List<AvlFeedDef> = emptyList(),
) {
    data class Http(
        val connectTimeoutMs: Long = 5_000,
        val readTimeoutMs: Long = 15_000,
        val maxSizeBytes: Long = 33_554_432,
        val userAgent: String = "transittrack/0.0.1",
    )

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

    data class AvlFeedDef(
        val code: String,
        val name: String,
        val gtfsFeedCode: String,
        val url: String,
        val format: AvlFormat = AvlFormat.GTFS_RT,
        val pollIntervalSec: Int = 15,
        val assignmentMode: AvlAssignmentMode = AvlAssignmentMode.FULL_INFERENCE,
        val enabled: Boolean = true,
        val headers: Map<String, String> = emptyMap(),
    )
}
