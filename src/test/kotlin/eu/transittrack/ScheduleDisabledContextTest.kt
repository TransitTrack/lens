package eu.transittrack

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import

/**
 * `ScheduleWriter` is `@ConditionalOnProperty(transittrack.schedule.enabled)`, so with the toggle
 * off the bean is absent. `DraftService` injects it via `ObjectProvider`, so the context must still
 * refresh — this guards against regressing to a hard dependency.
 */
@Import(TestcontainersConfiguration::class)
@SpringBootTest(properties = ["transittrack.schedule.enabled=false"])
class ScheduleDisabledContextTest {
    @Test
    fun contextLoads() {
    }
}
