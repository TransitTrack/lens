package eu.transittrack.gtfs.ingest

import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
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
                .derivationService(revisionService, revisions),
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
        assertThat(loaded.status).isEqualTo(GtfsRevisionStatus.ACTIVE)
        assertThat(routes.findByRevisionId(rev.id!!)).hasSize(1)
        assertThat(stopTimes.findByTripId(rev.id!!, "T1")).hasSize(2)
        assertThat(shapePoints.findByShapeId(rev.id!!, "SH1")).hasSize(2)
        assertThat(shapes.findByShapeId(rev.id!!, "SH1")!!.pointCount).isEqualTo(2)
        assertThat(loaded.feedStartDate).isEqualTo(LocalDate.of(2026, 1, 1))
        assertThat(feeds.findByCode("f")!!.lastIngestAt).isNotNull()
    }

    @Test
    fun `missing required file fails the revision with no rows`() {
        feed()
        val rev = service("missing-required-file").ingestBlocking("f")

        val loaded = revisions.findById(rev.id!!).get()
        assertThat(loaded.status).isEqualTo(GtfsRevisionStatus.FAILED)
        assertThat(loaded.errorMessage).isNotNull().contains("1 error")
        assertThat(routes.findByRevisionId(rev.id!!)).isEmpty()
    }

    @Test
    fun `malformed csv fails and cleans up`() {
        feed()
        val rev = service("malformed-csv").ingestBlocking("f")

        assertThat(revisions.findById(rev.id!!).get().status).isEqualTo(GtfsRevisionStatus.FAILED)
        assertThat(routes.findByRevisionId(rev.id!!)).isEmpty()
    }

    @Test
    fun `second identical import is UNCHANGED`() {
        feed()
        val svc = service("minimal-valid")
        svc.ingestBlocking("f")
        val second = svc.ingestBlocking("f")

        assertThat(revisions.findById(second.id!!).get().status).isEqualTo(GtfsRevisionStatus.UNCHANGED)
    }

    @Test
    fun `refuses concurrent ingest`() {
        val f = feed()
        revisionService.createPending(f.id!!, "u")

        assertFailure { service("minimal-valid").ingestBlocking("f") }.isInstanceOf<IllegalStateException>()
    }

    @Test
    fun `lenient mode records the validation report but still activates`() {
        feed()
        val rev = service("dangling-refs").ingestBlocking("f")

        val loaded = revisions.findById(rev.id!!).get()
        assertThat(loaded.status).isEqualTo(GtfsRevisionStatus.ACTIVE)
        assertThat(loaded.validationReport).isNotNull().contains("foreign_key_violation")
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
                    .derivationService(revisionService, revisions),
                eu.transittrack.gtfs.support
                    .postProcessors(),
            )
        val rev = strict.ingestBlocking("f")

        assertThat(revisions.findById(rev.id!!).get().status).isEqualTo(GtfsRevisionStatus.FAILED)
        assertThat(routes.findByRevisionId(rev.id!!)).isEmpty()
        assertThat(stopTimes.findByRevisionId(rev.id!!)).isEmpty()
    }

    @Test
    fun `changed import supersedes the first and keeps history`() {
        feed()
        val first = service("minimal-valid").ingestBlocking("f")
        val second = service("minimal-valid-v2").ingestBlocking("f")

        assertThat(revisions.findById(first.id!!).get().status).isEqualTo(GtfsRevisionStatus.SUPERSEDED)
        assertThat(revisions.findById(second.id!!).get().status).isEqualTo(GtfsRevisionStatus.ACTIVE)
        assertThat(revisions.findById(first.id!!).isPresent).isTrue()
        assertThat(revisions.findById(second.id!!).isPresent).isTrue()
        assertThat(routes.findByRevisionId(second.id!!)).hasSize(2)
    }
}
