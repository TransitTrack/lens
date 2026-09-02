package eu.transittrack.gtfs.ingest

import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

import org.junit.jupiter.api.AfterEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Import
import org.springframework.core.task.SyncTaskExecutor
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

import eu.transittrack.gtfs.GtfsProperties
import eu.transittrack.gtfs.feed.FeedSource
import eu.transittrack.gtfs.feed.GtfsFeed
import eu.transittrack.gtfs.feed.GtfsFeedRepository
import eu.transittrack.gtfs.feed.GtfsFeedService
import eu.transittrack.gtfs.model.RouteRepository
import eu.transittrack.gtfs.model.ShapePointRepository
import eu.transittrack.gtfs.model.ShapeRepository
import eu.transittrack.gtfs.model.StopTimeRepository
import eu.transittrack.gtfs.revision.GtfsRevisionRepository
import eu.transittrack.gtfs.revision.GtfsRevisionStatus
import eu.transittrack.gtfs.revision.RevisionService
import eu.transittrack.gtfs.store.RevisionWriter
import eu.transittrack.gtfs.store.StatelessSessionRevisionWriter
import eu.transittrack.gtfs.support.FixtureDownloader
import eu.transittrack.gtfs.support.PostgresSliceTest
import eu.transittrack.gtfs.validate.GtfsFeedLoader
import eu.transittrack.schedule.derive.ScheduleWriter

/**
 * [RevisionWriter] and the pieces of [RevisionService] it calls commit on their own connections, so — like [eu.transittrack.gtfs.revision.RevisionServiceTest] — this must NOT run inside the `@DataJpaTest` rollback transaction. Hence `NOT_SUPPORTED` plus
 * explicit `@AfterEach` cleanup.
 */
@PostgresSliceTest
@EnableConfigurationProperties(GtfsProperties::class)
@Import(
    StatelessSessionRevisionWriter::class,
    RevisionService::class,
    GtfsFeedService::class,
    ScheduleWriter::class,
)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class IngestionServiceTest(
    @Autowired val feeds: GtfsFeedRepository,
    @Autowired val revisions: GtfsRevisionRepository,
    @Autowired val revisionService: RevisionService,
    @Autowired val feedService: GtfsFeedService,
    @Autowired val writer: RevisionWriter,
    @Autowired val props: GtfsProperties,
    @Autowired val routes: RouteRepository,
    @Autowired val stopTimes: StopTimeRepository,
    @Autowired val shapes: ShapeRepository,
    @Autowired val shapePoints: ShapePointRepository,
    @Autowired val scheduleWriter: ScheduleWriter,
) {
    private val createdFeeds = mutableListOf<Long>()
    private val validator = GtfsFeedLoader()

    private fun feed(): GtfsFeed {
        val f =
            feeds.save(
                GtfsFeed(
                    "f",
                    "F",
                    null,
                    "http://x/g.zip",
                    null,
                    true,
                    true,
                    FeedSource.API,
                    Instant.now(),
                    Instant.now(),
                ),
            )
        createdFeeds += f.id!!
        return f
    }

    private fun service(fixture: String) =
        IngestionService(
            FixtureDownloader(fixture),
            revisionService,
            writer,
            feeds,
            feedService,
            props,
            shapes,
            validator,
            SyncTaskExecutor(),
            org.mockito.kotlin.mock<org.springframework.context.ApplicationEventPublisher>(),
            eu.transittrack.gtfs.support
                .postProcessors(),
        )

    @AfterEach
    fun cleanup() {
        for (feedId in createdFeeds) {
            val revs = revisions.findByFeedNewestFirst(feedId)
            for (r in revs) writer.deleteAllForRevision(r.id!!)
            revisions.deleteAllInBatch(revs)
            feeds.deleteById(feedId)
        }
        createdFeeds.clear()
    }

    @Test
    fun `happy path loads and activates a revision`() {
        feed()
        val rev = service("minimal-valid").ingestBlocking("f")

        val loaded = revisions.findById(rev.id!!).get()
        assertEquals(GtfsRevisionStatus.ACTIVE, loaded.status)
        assertEquals(1, routes.findByRevisionId(rev.id!!).size)
        assertEquals(2, stopTimes.findByTripId(rev.id!!, "T1").size)
        assertEquals(2, shapePoints.findByShapeId(rev.id!!, "SH1").size)
        assertEquals(2, shapes.findByShapeId(rev.id!!, "SH1")!!.pointCount)
        assertEquals(LocalDate.of(2026, 1, 1), loaded.feedStartDate)
        assertNotNull(feeds.findByCode("f")!!.lastIngestAt)
    }

    @Test
    fun `missing required file fails the revision with no rows`() {
        feed()
        val rev = service("missing-required-file").ingestBlocking("f")

        val loaded = revisions.findById(rev.id!!).get()
        assertEquals(GtfsRevisionStatus.FAILED, loaded.status)
        assertTrue(loaded.errorMessage!!.contains("1 error"))
        assertEquals(0, routes.findByRevisionId(rev.id!!).size)
    }

    @Test
    fun `malformed csv fails and cleans up`() {
        feed()
        val rev = service("malformed-csv").ingestBlocking("f")

        assertEquals(GtfsRevisionStatus.FAILED, revisions.findById(rev.id!!).get().status)
        assertEquals(0, routes.findByRevisionId(rev.id!!).size)
    }

    @Test
    fun `second identical import is UNCHANGED`() {
        feed()
        val svc = service("minimal-valid")
        svc.ingestBlocking("f")
        val second = svc.ingestBlocking("f")

        assertEquals(GtfsRevisionStatus.UNCHANGED, revisions.findById(second.id!!).get().status)
    }

    @Test
    fun `refuses concurrent ingest`() {
        val f = feed()
        revisionService.createPending(f.id!!, "u")

        assertFailsWith<IllegalStateException> { service("minimal-valid").ingestBlocking("f") }
    }

    @Test
    fun `lenient mode records the validation report but still activates`() {
        feed()
        val rev = service("dangling-refs").ingestBlocking("f")

        val loaded = revisions.findById(rev.id!!).get()
        assertEquals(GtfsRevisionStatus.ACTIVE, loaded.status)
        assertTrue(loaded.validationReport!!.contains("foreign_key_violation"))
    }

    @Test
    fun `strict mode fails on referential errors`() {
        feed()
        val strict =
            IngestionService(
                FixtureDownloader("dangling-refs"),
                revisionService,
                writer,
                feeds,
                feedService,
                GtfsProperties(ingest = GtfsProperties.Ingest(strictValidation = true)),
                shapes,
                validator,
                SyncTaskExecutor(),
                org.mockito.kotlin.mock<org.springframework.context.ApplicationEventPublisher>(),
                eu.transittrack.gtfs.support
                    .postProcessors(),
            )
        val rev = strict.ingestBlocking("f")

        assertEquals(GtfsRevisionStatus.FAILED, revisions.findById(rev.id!!).get().status)
        assertEquals(0, routes.findByRevisionId(rev.id!!).size)
        assertEquals(0, stopTimes.findByRevisionId(rev.id!!).size)
    }

    @Test
    fun `changed import supersedes the first and keeps history`() {
        feed()
        val first = service("minimal-valid").ingestBlocking("f")
        val second = service("minimal-valid-v2").ingestBlocking("f")

        assertEquals(GtfsRevisionStatus.SUPERSEDED, revisions.findById(first.id!!).get().status)
        assertEquals(GtfsRevisionStatus.ACTIVE, revisions.findById(second.id!!).get().status)
        assertTrue(revisions.findById(first.id!!).isPresent)
        assertTrue(revisions.findById(second.id!!).isPresent)
        assertEquals(2, routes.findByRevisionId(second.id!!).size)
    }
}
