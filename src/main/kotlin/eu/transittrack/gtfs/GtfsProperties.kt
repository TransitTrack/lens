package eu.transittrack.gtfs

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.NestedConfigurationProperty

@ConfigurationProperties("transittrack.gtfs")
data class GtfsProperties(
    @NestedConfigurationProperty val download: Download = Download(),
    @NestedConfigurationProperty val ingest: Ingest = Ingest(),
    @NestedConfigurationProperty val retention: Retention = Retention(),
    @NestedConfigurationProperty val polling: Polling = Polling(),
    @NestedConfigurationProperty val titleSanitizing: TitleSanitizing = TitleSanitizing(),
    val pruneConfigFeeds: Boolean = false,
    val feeds: List<FeedDef> = emptyList(),
) {
    data class Download(
        val connectTimeoutMs: Long = 10_000,
        val readTimeoutMs: Long = 60_000,
        val maxSizeBytes: Long = 524_288_000,
        val userAgent: String = "transittrack-explorer/0.0.1",
    )

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

    data class FeedDef(
        val code: String,
        val name: String,
        val description: String? = null,
        val url: String,
        val pollingCron: String? = null,
        val enabled: Boolean = true,
        val autoActivate: Boolean? = null,
    )

    data class TitleSanitizing(
        val enabled: Boolean = false,
        val capitalizeNames: Boolean = false,
        val regexReplaceListFileName: String? = null,
    )
}
