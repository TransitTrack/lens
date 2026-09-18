package eu.transittrack

import java.time.Duration

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.NestedConfigurationProperty

import eu.transittrack.predict.PredictionAlgorithm
import eu.transittrack.predict.PredictionMode

/**
 * Shared outbound HTTP client settings, used for both GTFS feed downloads and AVL feed polling.
 * Defaults are the permissive union of the two former per-module blocks — large enough for a GTFS
 * zip, harmless as a ceiling for a small AVL protobuf.
 */
@ConfigurationProperties("transittrack.http")
data class HttpClientProperties(
    val connectTimeoutMs: Long = 10_000,
    val readTimeoutMs: Long = 60_000,
    val maxSizeBytes: Long = 524_288_000,
    val userAgent: String = "transittrack/0.0.1",
)

@ConfigurationProperties("transittrack.gtfs")
data class GtfsProperties(
    @NestedConfigurationProperty val ingest: Ingest = Ingest(),
    @NestedConfigurationProperty val retention: Retention = Retention(),
    @NestedConfigurationProperty val polling: Polling = Polling(),
    @NestedConfigurationProperty val titleSanitizing: TitleSanitizing = TitleSanitizing(),
) {
    data class Ingest(
        val autoActivate: Boolean = true,
        val strictValidation: Boolean = false,
        val batchSize: Int = 1000,
        val tempDir: String = "",
    )

    data class Retention(
        val keepRevisionsPerFeed: Int = 5,
    )

    data class Polling(
        val enabled: Boolean = false,
        val sweepCron: String = "0 0 * * * *",
    )

    data class TitleSanitizing(
        val enabled: Boolean = false,
        val capitalizeNames: Boolean = false,
        val regexReplaceListFileName: String? = null,
    )
}

/**
 * Single source of truth for feed definitions. Each entry describes a GTFS schedule feed and,
 * optionally, its real-time vehicle-positions companion under [FeedDef.avl]. `GtfsFeedConfigSynchronizer`
 * upserts every entry into `gtfs_feed`; `AvlFeedConfigSynchronizer` upserts the entries that carry an
 * `avl` block into `avl_feed`, linked back to the same feed code.
 */
@ConfigurationProperties("transittrack.feed")
data class FeedsProperties(
    val pruneConfigFeeds: Boolean = false,
    val feeds: List<FeedDef> = emptyList(),
) {
    data class FeedDef(
        val code: String,
        val name: String,
        val description: String? = null,
        val url: String,
        val pollingCron: String? = null,
        val enabled: Boolean = true,
        val autoActivate: Boolean? = null,
        @NestedConfigurationProperty val avl: AvlDef? = null,
    )

    data class AvlDef(
        val url: String,
        val name: String? = null,
        val format: AvlFormat = AvlFormat.GTFS_RT,
        val pollIntervalSec: Int = 15,
        val assignmentMode: AvlAssignmentMode = AvlAssignmentMode.FULL_INFERENCE,
        val enabled: Boolean = true,
        val headers: Map<String, String> = emptyMap(),
        val predictionAlgorithm: PredictionAlgorithm = PredictionAlgorithm.SCHEDULE_ADHERENCE,
        val predictionMode: PredictionMode = PredictionMode.SINGLE,
    )
}

@ConfigurationProperties("transittrack.schedule")
data class ScheduleProperties(
    val enabled: Boolean = true,
    val layoverThresholdSec: Int = 60,
    val stopProjectionMaxDeviationM: Double = 100.0,
    val tolerateNoScheduleTrips: Boolean = false,
    val inferredBlocks: InferredBlocks = InferredBlocks(),
) {
    data class InferredBlocks(
        val enabled: Boolean = true,
        val allowDeadhead: Boolean = false,
        val maxDeadheadGapSec: Int = 1800,
    )
}

/**
 * Tuning for the AVL match read cache (see [eu.transittrack.avl.match.cache.AvlCaches]). Heap-only
 * Caffeine; one shared cap and TTL across all caches — per-cache tuning can come later if profiling
 * demands it.
 */
@ConfigurationProperties("transittrack.cache")
data class CacheProperties(
    val ttl: Duration = Duration.ofMinutes(60),
    val maxEntries: Long = 20_000,
)
