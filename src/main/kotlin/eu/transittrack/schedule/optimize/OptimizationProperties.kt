package eu.transittrack.schedule.optimize

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.NestedConfigurationProperty

@ConfigurationProperties("transittrack.schedule.optimize")
data class OptimizationProperties(
    @NestedConfigurationProperty val executor: Executor = Executor(),
    @NestedConfigurationProperty val retention: Retention = Retention(),
    /** Minimum absolute delta (seconds) a stop-time/trip-shift candidate must clear to be proposed (design 6.1). */
    val materialitySec: Int = 30,
) {
    /** Sizing for the dedicated one-core bounded executor that runs queued analysis jobs. */
    data class Executor(
        val queueCapacity: Int = 50,
    )

    /** How long to keep completed [eu.transittrack.schedule.optimize.model.OptimizationRunRow]s (design section 9). */
    data class Retention(
        val resultsRetentionDays: Int = 90,
    )
}
