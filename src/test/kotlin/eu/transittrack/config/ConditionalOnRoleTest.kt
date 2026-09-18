package eu.transittrack.config

import kotlin.test.Test

import org.springframework.boot.test.context.assertj.AssertableApplicationContext
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

class ConditionalOnRoleTest {
    @Configuration
    class RoleGatedBeans {
        @Bean
        @ConditionalOnRole(Role.FEED_PROCESSOR)
        fun feedProcessorMarker(): String = "feed-processor"

        @Bean
        @ConditionalOnRole(Role.PREDICTOR)
        fun predictorMarker(): Int = 1
    }

    private val runner = ApplicationContextRunner().withUserConfiguration(RoleGatedBeans::class.java)

    private fun AssertableApplicationContext.hasFeedProcessorBean() = this.containsBean("feedProcessorMarker")

    private fun AssertableApplicationContext.hasPredictorBean() = this.containsBean("predictorMarker")

    @Test
    fun `no role profile active means every role's beans are present`() {
        runner.run { ctx ->
            assert(ctx.hasFeedProcessorBean())
            assert(ctx.hasPredictorBean())
        }
    }

    @Test
    fun `only the matching role profile's bean is present when a role profile is set`() {
        runner.withPropertyValues("spring.profiles.active=role-feed-processor").run { ctx ->
            assert(ctx.hasFeedProcessorBean())
            assert(!ctx.hasPredictorBean())
        }
    }

    @Test
    fun `multiple role profiles can be active together`() {
        runner.withPropertyValues("spring.profiles.active=role-feed-processor,role-predictor").run { ctx ->
            assert(ctx.hasFeedProcessorBean())
            assert(ctx.hasPredictorBean())
        }
    }
}
