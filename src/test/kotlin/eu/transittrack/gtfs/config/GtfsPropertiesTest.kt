package eu.transittrack.gtfs.config

import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Configuration

import eu.transittrack.gtfs.GtfsProperties

class GtfsPropertiesTest {
    private val runner = ApplicationContextRunner().withUserConfiguration(TestConfig::class.java)

    @Configuration
    @EnableConfigurationProperties(GtfsProperties::class)
    class TestConfig

    @Test
    fun `binds retention`() {
        runner
            .withPropertyValues(
                "transittrack.gtfs.retention.keep-revisions-per-feed=3",
            ).run { ctx ->
                val props = ctx.getBean(GtfsProperties::class.java)
                assertThat(props.retention.keepRevisionsPerFeed).isEqualTo(3)
            }
    }
}
