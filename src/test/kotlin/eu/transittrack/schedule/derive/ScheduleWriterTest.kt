package eu.transittrack.schedule.derive

import java.time.Instant
import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.junit.jupiter.api.AfterEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

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
import eu.transittrack.support.PostgresPerMethodTest

/** Like IngestionServiceTest, ScheduleWriter commits on its own connection, so this must not run inside the @DataJpaTest rollback transaction. */
@PostgresSliceTest
@Import(ScheduleWriter::class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ScheduleWriterTest(
    @Autowired val feeds: GtfsFeedRepository,
    @Autowired val revisions: GtfsRevisionRepository,
    @Autowired val patterns: TripPatternRepository,
    @Autowired val stopPaths: StopPathRepository,
    @Autowired val writer: ScheduleWriter,
) : PostgresPerMethodTest() {
    private var rev: Long = 0
    private var feedId: Long = 0

    private fun seed() {
        val feed =
            feeds.save(
                GtfsFeed(
                    "sw",
                    "SW",
                    null,
                    "http://x/z.zip",
                    null,
                    true,
                    null,
                    FeedSource.API,
                    Instant.now(),
                    Instant.now(),
                ),
            )
        feedId = feed.id!!
        rev =
            revisions
                .save(
                    GtfsRevision(
                        feedId = feedId,
                        status = GtfsRevisionStatus.READY,
                        sourceUrl = "http://x/z.zip",
                        createdAt = Instant.now(),
                    ),
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
        val tp = TripPattern(rev, "K", "RA", null, 0, "H", "SHP", 1, null, tripCount = 0)
        writer.write(listOf(tp))
        val savedTp = patterns.findByPatternKey(rev, "K")!!
        val sp =
            StopPath(
                rev,
                savedTp.id!!,
                0,
                "S1",
                null,
                1,
                0.0,
                null,
                null,
                null,
                false,
                false,
                false,
                null,
            )
        writer.write(listOf(sp))

        writer.applyTripPatternTripCount(mapOf(savedTp.id!! to 3))
        val spId = stopPaths.findByTripPatternOrdered(rev, savedTp.id!!).single().id!!
        writer.applyStopPathLayover(listOf(StopPathLayoverUpdate(spId, true, 300)))

        assertThat(patterns.findByPatternKey(rev, "K")!!.tripCount).isEqualTo(3)
        val reloaded = stopPaths.findByTripPatternOrdered(rev, savedTp.id!!).single()
        assertThat(reloaded.breakTimeSec).isEqualTo(300)
        assertThat(reloaded.layoverStop).isEqualTo(true)

        writer.deleteForRevision(rev)
        assertThat(patterns.findByRevisionId(rev)).isEmpty()
        assertThat(stopPaths.findByTripPatternOrdered(rev, savedTp.id!!)).isEmpty()
    }

    @Test
    fun `deleteTables removes only the named tables`() {
        seed()
        val tp = TripPattern(rev, "K2", "RA", null, 0, "H", "SHP", 1, null, tripCount = 0)
        writer.write(listOf(tp))
        val savedTp = patterns.findByPatternKey(rev, "K2")!!
        writer.deleteTables(rev, "trip_patterns")
        assertThat(patterns.findByRevisionId(rev)).isEmpty()
    }

    @Test
    fun `resetTripPatternTripCount zeroes the column`() {
        seed()
        writer.write(listOf(TripPattern(rev, "K3", "RA", null, 0, "H", "SHP", 1, null, tripCount = 0)))
        val id = patterns.findByPatternKey(rev, "K3")!!.id!!
        writer.applyTripPatternTripCount(mapOf(id to 5))
        assertThat(patterns.findById(id).get().tripCount).isEqualTo(5)
        writer.resetTripPatternTripCount(rev)
        assertThat(patterns.findById(id).get().tripCount).isEqualTo(0)
    }
}
