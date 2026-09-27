package eu.transittrack.schedule.optimize

import java.util.concurrent.ThreadPoolExecutor

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor

/**
 * Dedicated one-core bounded executor for [ScheduleOptimizationService] analysis jobs: exactly one
 * run analyzed at a time, isolated from the GTFS ingest executor so a long-running optimization
 * never delays ingest/rebuild jobs (and vice versa).
 */
@Configuration
class ScheduleOptimizeExecutorConfiguration(
    private val optimizeProps: OptimizationProperties,
    @Value("\${spring.threads.virtual.enabled:false}") private val useVirtualThreads: Boolean,
) {
    @Bean
    fun scheduleOptimizationExecutor(): ThreadPoolTaskExecutor =
        ThreadPoolTaskExecutor().apply {
            corePoolSize = 1
            maxPoolSize = 1
            queueCapacity = optimizeProps.executor.queueCapacity
            setThreadNamePrefix("schedule-optimize-")
            setVirtualThreads(useVirtualThreads)
            setRejectedExecutionHandler(ThreadPoolExecutor.CallerRunsPolicy())
            initialize()
        }
}
