package eu.transittrack.schedule.derive

import eu.transittrack.gtfs.feed.FeedSource
import eu.transittrack.gtfs.feed.GtfsFeed
import eu.transittrack.gtfs.feed.GtfsFeedRepository
import eu.transittrack.gtfs.revision.GtfsRevision
import eu.transittrack.gtfs.revision.GtfsRevisionRepository
import eu.transittrack.gtfs.revision.GtfsRevisionStatus
import eu.transittrack.gtfs.support.PostgresSliceTest
import eu.transittrack.schedule.model.StopPath
import eu.transittrack.schedule.model.StopPathRepository
import eu.transittrack.schedule.model.TripPattern
import eu.transittrack.schedule.model.TripPatternRepository
import java.time.Instant
import org.junit.jupiter.api.AfterEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Like IngestionServiceTest, ScheduleWriter commits on its own connection, so this
 * must not run inside the @DataJpaTest rollback transaction.
 */
@PostgresSliceTest
@Import(ScheduleWriter::class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ScheduleWriterTest(
    @Autowired val feeds: GtfsFeedRepository,
    @Autowired val revisions: GtfsRevisionRepository,
    @Autowired val patterns: TripPatternRepository,
    @Autowired val stopPaths: StopPathRepository,
    @Autowired val writer: ScheduleWriter,
) {
    private var rev: Long = 0
    private var feedId: Long = 0

    private fun seed() {
        val feed = feeds.save(
            GtfsFeed("sw", "SW", null, "http://x/z.zip", null, true, null,
                FeedSource.API, Instant.now(), Instant.now()),
        )
        feedId = feed.id!!
        rev = revisions.save(
            GtfsRevision(feedId = feedId, status = GtfsRevisionStatus.READY,
                sourceUrl = "http://x/z.zip", createdAt = Instant.now()),
        ).id!!
    }

    @AfterEach
    fun cleanup() {
        writer.deleteForRevision(rev)
        revisions.findById(rev).ifPresent { revisions.delete(it) }
        feeds.deleteById(feedId)
    }

    @Test
    fun `write, aggregate-update, and delete round-trip`() {
        seed()
        val tp = TripPattern(rev, "K", "RA", 0, "H", "SHP", 1, null, null, null, null, null, 0)
        writer.write(listOf(tp))
        val savedTp = patterns.findByRevisionIdAndPatternKey(rev, "K")!!
        val sp = StopPath(rev, savedTp.id!!, 0, "S1", 1, 0.0, null, null, null,
            false, false, false, null, null, null)
        writer.write(listOf(sp))

        writer.applyTripPatternTripCount(mapOf(savedTp.id!! to 3))
        val spId = stopPaths.findByRevisionIdAndTripPatternIdOrderByStopPathIndex(rev, savedTp.id!!).single().id!!
        writer.applyStopPathAggregates(listOf(StopPathAggregateUpdate(spId, 42, 10, true, 300)))
        // null bindings must also work (see the NOTE in Step 4)
        writer.applyStopPathAggregates(listOf(StopPathAggregateUpdate(spId, null, null, true, null)))
        writer.applySchedTripBlockFields(listOf(SchedTripBlockUpdate(-1L, 0, null, null)))  // no-op, just exercises null binds

        assertEquals(3, patterns.findByRevisionIdAndPatternKey(rev, "K")!!.tripCount)
        val reloaded = stopPaths.findByRevisionIdAndTripPatternIdOrderByStopPathIndex(rev, savedTp.id!!).single()
        assertEquals(null, reloaded.typicalTravelTimeSec)
        assertEquals(true, reloaded.layoverStop)

        writer.deleteForRevision(rev)
        assertEquals(0, patterns.findByRevisionId(rev).size)
        assertEquals(0, stopPaths.findByRevisionIdAndTripPatternIdOrderByStopPathIndex(rev, savedTp.id!!).size)
    }
}
