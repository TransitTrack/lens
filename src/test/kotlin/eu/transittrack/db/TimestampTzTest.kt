package eu.transittrack.db

import java.time.Instant

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

import eu.transittrack.AvlAssignmentMode
import eu.transittrack.AvlFormat
import eu.transittrack.avl.model.AvlFeed
import eu.transittrack.avl.model.AvlFeedRepository
import eu.transittrack.avl.model.AvlFeedSourceKind
import eu.transittrack.support.DstTimeZoneExtension
import eu.transittrack.support.PostgresPerMethodTest

/**
 * `timestamptz` columns ([RowTable.timestampTz]) store and read back the exact instant even inside a
 * DST fall-back hour of the JVM's zone, where Exposed's `timestamp()` shifted it by the overlap.
 */
@SpringBootTest
@ExtendWith(DstTimeZoneExtension::class)
class TimestampTzTest(
    @Autowired val feeds: AvlFeedRepository,
    @Autowired val db: Database,
) : PostgresPerMethodTest() {
    @Test
    fun instantInsideDstFallBackHourRoundTrips() {
        val instant = DstTimeZoneExtension.DST_FALL_BACK
        feeds.save(feed(lastPollAt = instant))

        assertThat(feeds.findByCode("dst")!!.lastPollAt).isEqualTo(instant)
        val storedExactly =
            transaction(db) {
                exec("select last_poll_at = timestamptz '$instant' from avl_feed where code = 'dst'") { rs ->
                    rs.next()
                    rs.getBoolean(1)
                }
            }
        assertThat(storedExactly == true, name = "stored as $instant").isTrue()
    }

    private fun feed(lastPollAt: Instant) =
        AvlFeed(
            code = "dst",
            name = "DST",
            gtfsFeedCode = "g-dst",
            url = "http://x/dst",
            format = AvlFormat.GTFS_RT,
            pollIntervalSec = 30,
            assignmentMode = AvlAssignmentMode.TRUST_DESCRIPTOR,
            enabled = false,
            headers = null,
            source = AvlFeedSourceKind.CONFIG,
            lastPollAt = lastPollAt,
            createdAt = Instant.parse("2026-09-01T00:00:00Z"),
            updatedAt = Instant.parse("2026-09-01T00:00:00Z"),
        )
}
