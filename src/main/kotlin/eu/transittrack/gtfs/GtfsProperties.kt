package eu.transittrack.gtfs

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.NestedConfigurationProperty

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
