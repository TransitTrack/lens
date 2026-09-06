package eu.transittrack.feed

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.NestedConfigurationProperty

import eu.transittrack.predict.PredictionAlgorithm
import eu.transittrack.predict.PredictionMode

enum class AvlFormat { GTFS_RT, STPT }

enum class AvlAssignmentMode { TRUST_DESCRIPTOR, DESCRIPTOR_THEN_INFER, FULL_INFERENCE }

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
