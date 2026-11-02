package eu.transittrack.support

import java.time.Duration
import java.time.Instant
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap

import net.javacrumbs.shedlock.core.LockConfiguration
import net.javacrumbs.shedlock.core.LockProvider
import net.javacrumbs.shedlock.core.SimpleLock

/**
 * Minimal in-memory [LockProvider] for unit tests that don't need a real database. Mirrors the
 * JDBC provider's semantics closely enough for tests: a name maps to a single `lockUntil` instant;
 * [SimpleLock.unlock] releases early unless `lockAtLeastFor` hasn't elapsed yet, in which case the
 * lock stays held until that deadline, same as the real provider.
 */
class FakeLockProvider : LockProvider {
    private data class Row(
        val lockUntil: Instant,
    )

    private val rows = ConcurrentHashMap<String, Row>()

    @Synchronized
    override fun lock(lockConfiguration: LockConfiguration): Optional<SimpleLock> {
        val now = Instant.now()
        val row = rows[lockConfiguration.name]
        if (row != null && row.lockUntil.isAfter(now)) {
            return Optional.empty()
        }
        rows[lockConfiguration.name] = Row(lockConfiguration.lockAtMostUntil)
        return Optional.of(FakeLock(lockConfiguration))
    }

    private inner class FakeLock(
        private val config: LockConfiguration,
    ) : SimpleLock {
        @Synchronized
        override fun unlock() {
            val now = Instant.now()
            val releaseAt = config.lockAtLeastUntil
            rows[config.name] = Row(if (releaseAt.isAfter(now)) releaseAt else now)
        }

        @Synchronized
        override fun extend(
            lockAtMostFor: Duration,
            lockAtLeastFor: Duration,
        ): Optional<SimpleLock> {
            val newConfig = LockConfiguration(Instant.now(), config.name, lockAtMostFor, lockAtLeastFor)
            rows[config.name] = Row(newConfig.lockAtMostUntil)
            return Optional.of(FakeLock(newConfig))
        }
    }
}
