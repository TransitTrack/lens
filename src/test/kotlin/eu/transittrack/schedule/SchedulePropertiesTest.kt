package eu.transittrack.schedule

import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Configuration

class SchedulePropertiesTest {
    private val runner = ApplicationContextRunner().withUserConfiguration(TestConfig::class.java)

    @Configuration
    @EnableConfigurationProperties(ScheduleProperties::class)
    class TestConfig

    @Test
    fun `tolerateNoScheduleTrips defaults false and binds`() {
        runner.run { ctx ->
            assertThat(ctx.getBean(ScheduleProperties::class.java).tolerateNoScheduleTrips).isFalse()
        }
        runner.withPropertyValues("transittrack.schedule.tolerate-no-schedule-trips=true").run { ctx ->
            assertThat(ctx.getBean(ScheduleProperties::class.java).tolerateNoScheduleTrips).isTrue()
        }
    }
}
