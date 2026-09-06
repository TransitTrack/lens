package eu.transittrack.http

import org.springframework.boot.context.properties.ConfigurationProperties

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
