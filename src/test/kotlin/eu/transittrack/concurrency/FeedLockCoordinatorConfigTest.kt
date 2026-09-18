package eu.transittrack.concurrency

import kotlin.test.Test

import net.javacrumbs.shedlock.core.LockProvider
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

import eu.transittrack.support.FakeLockProvider

class FeedLockCoordinatorConfigTest {
    @Configuration
    class FakeLockProviderConfig {
        @Bean
        fun lockProvider(): LockProvider = FakeLockProvider()
    }

    private val runner =
        ApplicationContextRunner()
            .withUserConfiguration(FakeLockProviderConfig::class.java, FeedLockCoordinatorConfig::class.java)

    @Test
    fun `no role profile active means both coordinators exist and are distinct instances`() {
        runner.run { ctx ->
            val avl = ctx.getBean("avlFeedLockCoordinator", FeedLockCoordinator::class.java)
            val predictor = ctx.getBean("predictorFeedLockCoordinator", FeedLockCoordinator::class.java)
            assert(avl !== predictor)
        }
    }

    @Test
    fun `only the predictor coordinator exists under role-predictor`() {
        runner.withPropertyValues("spring.profiles.active=role-predictor").run { ctx ->
            assert(!ctx.containsBean("avlFeedLockCoordinator"))
            assert(ctx.containsBean("predictorFeedLockCoordinator"))
        }
    }
}
