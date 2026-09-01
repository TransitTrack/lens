package eu.transittrack.gtfs.revision

import eu.transittrack.gtfs.config.GtfsProperties
import eu.transittrack.gtfs.feed.FeedSource
import eu.transittrack.gtfs.feed.GtfsFeed
import eu.transittrack.gtfs.feed.GtfsFeedRepository
import eu.transittrack.gtfs.model.GtfsCalendar
import eu.transittrack.gtfs.model.GtfsCalendarRepository
import eu.transittrack.gtfs.store.RevisionWriter
import eu.transittrack.gtfs.store.StatelessSessionRevisionWriter
import eu.transittrack.gtfs.support.PostgresSliceTest
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.AfterEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Import
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * [RevisionWriter.deleteAllForRevision] commits through its own stateless-session
 * transaction on a separate connection, so — like [StatelessSessionRevisionWriter]'s
 * own test — this must NOT run inside the `@DataJpaTest` rollback transaction, or the
 * writer would never see the seeded rows. Hence `NOT_SUPPORTED` + explicit cleanup.
 */
@PostgresSliceTest
@EnableConfigurationProperties(GtfsProperties::class)
@Import(StatelessSessionRevisionWriter::class, RevisionService::class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RevisionServiceTest(
    @Autowired val svc: RevisionService,
    @Autowired val revisions: GtfsRevisionRepository,
    @Autowired val feeds: GtfsFeedRepository,
    @Autowired val calendars: GtfsCalendarRepository,
    @Autowired val writer: RevisionWriter,
) {
    private val createdFeeds = mutableListOf<Long>()

    private fun feed(): Long {
        val id = feeds.save(
            GtfsFeed(
                "f-${System.nanoTime()}", "F", null, "u", null, true, null,
                FeedSource.API, Instant.now(), Instant.now(),
            ),
        ).id!!
        createdFeeds += id
        return id
    }

    @AfterEach
    fun cleanup() {
        for (feedId in createdFeeds) {
            for (r in revisions.findByFeedNewestFirst(feedId)) {
                writer.deleteAllForRevision(r.id!!)
            }
            revisions.deleteAllInBatch(revisions.findByFeedNewestFirst(feedId))
            feeds.deleteById(feedId)
        }
    }

    @Test fun `activate swaps the active pointer`() {
        val f = feed()
        val r1 = svc.createPending(f, "u"); svc.transition(r1.id!!, GtfsRevisionStatus.READY); svc.activate(r1.id!!)
        val r2 = svc.createPending(f, "u"); svc.transition(r2.id!!, GtfsRevisionStatus.READY); svc.activate(r2.id!!)
        assertEquals(GtfsRevisionStatus.SUPERSEDED, revisions.findById(r1.id!!).get().status)
        assertEquals(GtfsRevisionStatus.ACTIVE, revisions.findById(r2.id!!).get().status)
        assertEquals(r2.id, svc.activeRevisionId(f))
    }

    @Test fun `prune keeps active plus N terminal, never active`() {
        val f = feed()
        val active = svc.createPending(f, "u"); svc.transition(active.id!!, GtfsRevisionStatus.READY); svc.activate(active.id!!)
        (1..5).map {
            val r = svc.createPending(f, "u"); svc.transition(r.id!!, GtfsRevisionStatus.SUPERSEDED); r.id!!
        }
        svc.prune(f, keep = 2)
        assertTrue(revisions.findById(active.id!!).isPresent)
        assertEquals(2, revisions.findByFeedNewestFirst(f).count { it.status == GtfsRevisionStatus.SUPERSEDED })
    }

    @Test fun `deriveDates falls back to calendar span`() {
        val f = feed()
        val r = svc.createPending(f, "u")
        calendars.save(
            GtfsCalendar(
                r.id!!, "WK", true, true, true, true, true, false, false,
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 9, 30),
            ),
        )
        svc.deriveDates(r.id!!)
        val loaded = revisions.findById(r.id!!).get()
        assertEquals(LocalDate.of(2026, 3, 1), loaded.feedStartDate)
        assertEquals(LocalDate.of(2026, 9, 30), loaded.feedEndDate)
    }

    @Test fun `fail deletes rows and records message`() {
        val f = feed()
        val r = svc.createPending(f, "u")
        calendars.save(GtfsCalendar(r.id!!, "WK", true, null, null, null, null, null, null, null, null))
        svc.fail(r.id!!, "boom")
        assertEquals(GtfsRevisionStatus.FAILED, revisions.findById(r.id!!).get().status)
        assertEquals("boom", revisions.findById(r.id!!).get().errorMessage)
        assertEquals(0, calendars.findByRevisionId(r.id!!).size)
    }
}
