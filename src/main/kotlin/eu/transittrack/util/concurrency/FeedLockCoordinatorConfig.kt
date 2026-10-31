package eu.transittrack.util.concurrency

import java.time.Duration

import net.javacrumbs.shedlock.core.LockProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

import eu.transittrack.ConditionalOnRole
import eu.transittrack.Role

@Configuration
class FeedLockCoordinatorConfig {
    @Bean("avlFeedLockCoordinator")
    @ConditionalOnRole(Role.FEED_PROCESSOR)
    fun avlFeedLockCoordinator(lockProvider: LockProvider): FeedLockCoordinator =
        FeedLockCoordinator(lockProvider, "avl-feed", Duration.ofMinutes(3), Duration.ofSeconds(50))

    @Bean("predictorFeedLockCoordinator")
    @ConditionalOnRole(Role.PREDICTOR)
    fun predictorFeedLockCoordinator(lockProvider: LockProvider): FeedLockCoordinator =
        FeedLockCoordinator(lockProvider, "predictor-feed", Duration.ofMinutes(3), Duration.ofSeconds(50))
}
