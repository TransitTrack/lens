package eu.transittrack.util.concurrency

import net.javacrumbs.shedlock.core.DefaultLockingTaskExecutor
import net.javacrumbs.shedlock.core.LockProvider
import net.javacrumbs.shedlock.core.LockingTaskExecutor
import net.javacrumbs.shedlock.provider.exposed.ExposedLockProvider
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock
import org.jetbrains.exposed.v1.jdbc.Database
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT5M")
class SchedulerLockConfig {
    // Uses the database clock (CURRENT_TIMESTAMP), and opens its own top-level transaction per
    // operation, so lock and unlock commit independently of any surrounding JPA transaction.
    @Bean
    fun lockProvider(
        @Qualifier("exposedDatabase") database: Database,
    ): ExposedLockProvider = ExposedLockProvider(database)

    @Bean
    fun lockingTaskExecutor(lockProvider: LockProvider): LockingTaskExecutor = DefaultLockingTaskExecutor(lockProvider)
}
