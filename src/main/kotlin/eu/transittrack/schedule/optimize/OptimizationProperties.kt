package eu.transittrack.schedule.optimize

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.NestedConfigurationProperty

@ConfigurationProperties("transittrack.schedule.optimize")
data class OptimizationProperties(
    @NestedConfigurationProperty val executor: Executor = Executor(),
) {
    /** Sizing for the dedicated one-core bounded executor that runs queued analysis jobs. */
    data class Executor(
        val queueCapacity: Int = 50,
    )
}
