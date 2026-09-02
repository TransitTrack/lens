package eu.transittrack.gtfs.config

import kotlin.test.Test
import kotlin.test.assertEquals

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
                assertEquals(3, props.retention.keepRevisionsPerFeed)
                assertEquals(1, props.feeds.size)
                assertEquals("wroclaw", props.feeds[0].code)
                assertEquals("0 0 3 * * *", props.feeds[0].pollingCron)
                assertEquals(true, props.feeds[0].enabled)
            }
    }
}
