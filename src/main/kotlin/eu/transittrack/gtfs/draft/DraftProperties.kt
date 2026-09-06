package eu.transittrack.gtfs.draft

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("transittrack.draft")
data class DraftProperties(
    val editorLeaseMinutes: Long = 15,
)
