package eu.transittrack.gtfs.config

import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.hasSize
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
    fun `binds feeds and retention`() {
        runner
            .withPropertyValues(
                "transittrack.gtfs.retention.keep-revisions-per-feed=3",
                "transittrack.gtfs.feeds[0].code=wroclaw",
                "transittrack.gtfs.feeds[0].name=Wroclaw",
                "transittrack.gtfs.feeds[0].url=https://example.org/w.zip",
                "transittrack.gtfs.feeds[0].polling-cron=0 0 3 * * *",
            ).run { ctx ->
                val props = ctx.getBean(GtfsProperties::class.java)
                assertThat(props.retention.keepRevisionsPerFeed).isEqualTo(3)
                assertThat(props.feeds).hasSize(1)
                assertThat(props.feeds[0].code).isEqualTo("wroclaw")
                assertThat(props.feeds[0].pollingCron).isEqualTo("0 0 3 * * *")
                assertThat(props.feeds[0].enabled).isEqualTo(true)
            }
    }
}
