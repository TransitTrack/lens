package eu.transittrack.concurrency

import java.time.Duration
import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import net.javacrumbs.shedlock.core.LockProvider
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate

import eu.transittrack.support.PostgresPerMethodTest

@SpringBootTest(classes = [eu.transittrack.Application::class])
class FeedLockCoordinatorTest(
    @Autowired val jdbcTemplate: JdbcTemplate,
) : PostgresPerMethodTest() {
    // Two independent JdbcTemplateLockProvider instances sharing one DataSource stand in for
    // two pods contending over the same shedlock table rows.
    private fun newProvider(): LockProvider =
        JdbcTemplateLockProvider(
            JdbcTemplateLockProvider.Configuration
                .builder()
                .withJdbcTemplate(jdbcTemplate)
                .usingDbTime()
                .build(),
        )

    private fun coordinator(
        lockAtMostFor: Duration = Duration.ofMinutes(3),
        lockAtLeastFor: Duration = Duration.ofSeconds(50),
    ) = FeedLockCoordinator(newProvider(), "test-feed", lockAtMostFor, lockAtLeastFor)

    @Test
    fun `an uncontested coordinator claims every candidate feed`() {
        val a = coordinator()
        assertThat(a.reconcileOwnership(setOf("a", "b", "c"))).isEqualTo(setOf("a", "b", "c"))
    }

    @Test
    fun `a second coordinator cannot claim a feed already owned by the first`() {
        val a = coordinator()
        val b = coordinator()

        assertThat(a.reconcileOwnership(setOf("a"))).contains("a")
        assertThat(b.reconcileOwnership(setOf("a"))).doesNotContain("a")
    }

    @Test
    fun `ownership is retained across repeated reconcile ticks via extend`() {
        val a = coordinator()

        assertThat(a.reconcileOwnership(setOf("a"))).contains("a")
        val lockUntilAfterFirst = lockUntil("test-feed:a")

        assertThat(a.reconcileOwnership(setOf("a"))).contains("a")
        val lockUntilAfterSecond = lockUntil("test-feed:a")

        assert(lockUntilAfterSecond.isAfter(lockUntilAfterFirst))
    }

    @Test
    fun `release frees the feed immediately for another coordinator`() {
        // lockAtLeastFor is deliberately ~zero: a nonzero value keeps the lock held for that
        // minimum even across an explicit release()/unlock(), which is correct ShedLock behavior
        // but would make this "release frees it immediately" assertion flaky/wrong.
        val a = coordinator(lockAtLeastFor = Duration.ZERO)
        val b = coordinator()

        a.reconcileOwnership(setOf("a"))
        a.release("a")

        assertThat(b.reconcileOwnership(setOf("a"))).contains("a")
    }

    @Test
    fun `an expired lock can be reclaimed by another coordinator`() {
        val a = coordinator(lockAtMostFor = Duration.ofMillis(200), lockAtLeastFor = Duration.ZERO)
        val b = coordinator()

        a.reconcileOwnership(setOf("a"))
        Thread.sleep(400)

        assertThat(b.reconcileOwnership(setOf("a"))).contains("a")
    }

    @Test
    fun `a feed dropped from the candidate set is released`() {
        // Same lockAtLeastFor caveat as the explicit-release test above.
        val a = coordinator(lockAtLeastFor = Duration.ZERO)
        val b = coordinator()

        a.reconcileOwnership(setOf("a"))
        a.reconcileOwnership(emptySet())

        assertThat(b.reconcileOwnership(setOf("a"))).contains("a")
    }

    @Test
    fun `releaseAll frees every held feed for another coordinator`() {
        val a = coordinator(lockAtLeastFor = Duration.ZERO)
        val b = coordinator()

        a.reconcileOwnership(setOf("a", "b"))
        a.releaseAll()

        assertThat(b.reconcileOwnership(setOf("a", "b"))).isEqualTo(setOf("a", "b"))
    }

    private fun lockUntil(name: String) =
        jdbcTemplate.queryForObject(
            "select lock_until from shedlock where name = ?",
            java.time.Instant::class.java,
            name,
        )!!
}
