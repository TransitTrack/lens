package eu.transittrack.support

import java.time.Instant
import java.util.TimeZone

import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

/**
 * Runs each test with the JVM default time zone set to a zone that observes DST (Europe/Bucharest),
 * restoring the previous default afterwards, so [DST_FALL_BACK] samples exercise the local-time
 * overlap even on a machine whose zone is UTC. Tests run sequentially in one JVM, so the swap is
 * not seen by other test classes.
 */
class DstTimeZoneExtension :
    BeforeEachCallback,
    AfterEachCallback {
    override fun beforeEach(context: ExtensionContext) {
        context.getStore(NAMESPACE).put(KEY, TimeZone.getDefault())
        TimeZone.setDefault(TimeZone.getTimeZone(ZONE))
    }

    override fun afterEach(context: ExtensionContext) {
        context.getStore(NAMESPACE).remove(KEY, TimeZone::class.java)?.let(TimeZone::setDefault)
    }

    companion object {
        const val ZONE = "Europe/Bucharest"

        /** Inside Europe/Bucharest's 2026 fall-back hour: its local wall time (03:30) occurs twice. */
        val DST_FALL_BACK: Instant = Instant.parse("2026-10-25T01:30:00.654321Z")

        private val NAMESPACE = ExtensionContext.Namespace.create(DstTimeZoneExtension::class.java)
        private const val KEY = "previousDefault"
    }
}
