package eu.transittrack.schedule.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("transittrack.schedule")
data class ScheduleProperties(
    val enabled: Boolean = true,
    val layoverThresholdSec: Int = 60,
    val stopProjectionMaxDeviationM: Double = 100.0,
)
