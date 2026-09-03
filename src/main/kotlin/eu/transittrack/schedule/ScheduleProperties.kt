package eu.transittrack.schedule

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("transittrack.schedule")
data class ScheduleProperties(
    val enabled: Boolean = true,
    val layoverThresholdSec: Int = 60,
    val stopProjectionMaxDeviationM: Double = 100.0,
    val tolerateNoScheduleTrips: Boolean = false,
)
