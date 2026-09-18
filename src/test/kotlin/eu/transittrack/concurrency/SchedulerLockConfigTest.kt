package eu.transittrack.concurrency

import java.time.Duration
import java.time.Instant
import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import net.javacrumbs.shedlock.core.LockConfiguration
import net.javacrumbs.shedlock.core.LockProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

import eu.transittrack.support.PostgresPerMethodTest

@SpringBootTest(classes = [eu.transittrack.Application::class])
class SchedulerLockConfigTest(
    @Autowired val lockProvider: LockProvider,
) : PostgresPerMethodTest() {
    // lockAtLeastFor is deliberately ~zero here: a nonzero value keeps ShedLock's lock held for
    // that minimum even across an explicit unlock(), which is correct ShedLock behavior but would
    // make this "release frees it immediately" assertion flaky/wrong.
    private fun config(name: String) = LockConfiguration(Instant.now(), name, Duration.ofSeconds(30), Duration.ofMillis(1))

    @Test
    fun `a lock can be acquired, blocks a second acquisition, and is releasable`() {
        val first = lockProvider.lock(config("test-lock"))
        assertThat(first.isPresent).isTrue()

        val second = lockProvider.lock(config("test-lock"))
        assertThat(second.isPresent).isFalse()

        first.get().unlock()
        val third = lockProvider.lock(config("test-lock"))
        assertThat(third.isPresent).isTrue()
        third.get().unlock()
    }
}
