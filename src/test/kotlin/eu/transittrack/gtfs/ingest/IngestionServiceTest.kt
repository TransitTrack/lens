package eu.transittrack.gtfs.ingest

import java.nio.file.Files
import java.nio.file.Path
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

import eu.transittrack.GtfsProperties
import eu.transittrack.gtfs.download.DownloadedFeed
import eu.transittrack.gtfs.download.FeedDownloader
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
import eu.transittrack.gtfs.store.ExposedRevisionWriter
import eu.transittrack.gtfs.store.RevisionWriter
import eu.transittrack.gtfs.support.FixtureDownloader
import eu.transittrack.gtfs.support.PostgresSliceTest
import eu.transittrack.gtfs.validate.GtfsFeedLoader
import eu.transittrack.gtfs.validate.LoadValidationReport
import eu.transittrack.schedule.derive.ScheduleWriter
import eu.transittrack.support.PostgresPerMethodTest

/**
 * [RevisionWriter] and the pieces of [RevisionService] it calls commit on their own connections, so — like [eu.transittrack.gtfs.revision.RevisionServiceTest] — this must NOT run inside the `@DataJpaTest` rollback transaction. Hence `NOT_SUPPORTED` plus
 * explicit `@AfterEach` cleanup.
 */
@PostgresSliceTest
@EnableConfigurationProperties(GtfsProperties::class)
@Import(
    ExposedRevisionWriter::class,
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
) : PostgresPerMethodTest() {
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

    private fun service(
        fixture: String,
        strict: Boolean = false,
        downloader: FeedDownloader = FixtureDownloader(fixture),
    ) = IngestionService(
        downloader,
        revisionService,
        writer,
        feeds,
        feedService,
        if (strict) GtfsProperties(ingest = GtfsProperties.Ingest(strictValidation = true)) else props,
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
            revs.forEach(revisions::delete)
            feeds.deleteById(feedId)
        }
        createdFeeds.clear()
    }

    @Test
    fun `happy path loads and activates a revision`() {
        feed()
        val rev = service("minimal-valid").ingestBlocking("f")

        val loaded = revisions.findById(rev.id!!)!!
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

        val loaded = revisions.findById(rev.id!!)!!
        assertThat(loaded.status).isEqualTo(GtfsRevisionStatus.FAILED)
        assertThat(loaded.errorMessage).isNotNull().contains("1 error")
        assertThat(loaded.validationReport).isNotNull().contains("ARC_004")
        assertThat(routes.findByRevisionId(rev.id!!)).isEmpty()
    }

    @Test
    fun `malformed csv fails and cleans up`() {
        feed()
        val rev = service("malformed-csv").ingestBlocking("f")

        val loaded = revisions.findById(rev.id!!)!!
        assertThat(loaded.status).isEqualTo(GtfsRevisionStatus.FAILED)
        assertThat(loaded.validationReport).isNotNull().contains("ARC_013")
        assertThat(routes.findByRevisionId(rev.id!!)).isEmpty()
    }

    @Test
    fun `second identical import is UNCHANGED`() {
        feed()
        val svc = service("minimal-valid")
        svc.ingestBlocking("f")
        val second = svc.ingestBlocking("f")

        assertThat(revisions.findById(second.id!!)!!.status).isEqualTo(GtfsRevisionStatus.UNCHANGED)
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

        val loaded = revisions.findById(rev.id!!)!!
        assertThat(loaded.status).isEqualTo(GtfsRevisionStatus.ACTIVE)
        assertThat(loaded.validationReport).isNotNull().contains("TRP_002")
        assertThat(LoadValidationReport.fromJson(loaded.validationReport!!)).isNotNull()
    }

    @Test
    fun `strict mode fails when not publishable`() {
        feed()
        val rev = service("dangling-refs", strict = true).ingestBlocking("f")

        assertThat(revisions.findById(rev.id!!)!!.status).isEqualTo(GtfsRevisionStatus.FAILED)
        assertThat(routes.findByRevisionId(rev.id!!)).isEmpty()
        assertThat(stopTimes.findByRevisionId(rev.id!!)).isEmpty()
    }

    @Test
    fun `strict mode activates a publishable feed`() {
        feed()
        val rev = service("minimal-valid", strict = true).ingestBlocking("f")

        assertThat(revisions.findById(rev.id!!)!!.status).isEqualTo(GtfsRevisionStatus.ACTIVE)
    }

    @Test
    fun `non-zip download fails with fatal report`() {
        feed()
        val notAZip =
            object : FeedDownloader {
                override fun download(
                    url: String,
                    into: Path,
                ): DownloadedFeed {
                    Files.writeString(into, "not a zip")
                    return DownloadedFeed(into, "sha-not-a-zip", Files.size(into))
                }
            }
        val rev = service("unused", downloader = notAZip).ingestBlocking("f")

        val loaded = revisions.findById(rev.id!!)!!
        assertThat(loaded.status).isEqualTo(GtfsRevisionStatus.FAILED)
        assertThat(loaded.validationReport).isNotNull().contains("Fatal")
        assertThat(routes.findByRevisionId(rev.id!!)).isEmpty()
    }

    @Test
    fun `required file with every row skipped fails the revision`() {
        feed()
        val rev = service("stops-all-missing-id").ingestBlocking("f")

        val loaded = revisions.findById(rev.id!!)!!
        assertThat(loaded.status).isEqualTo(GtfsRevisionStatus.FAILED)
        assertThat(loaded.errorMessage).isNotNull().contains("stops.txt")
    }

    @Test
    fun `row missing a required key is skipped`() {
        feed()
        val rev = service("stop-missing-id").ingestBlocking("f")

        val loaded = revisions.findById(rev.id!!)!!
        assertThat(loaded.status).isEqualTo(GtfsRevisionStatus.ACTIVE)
        assertThat(loaded.rowCounts["gtfs_stop"]).isEqualTo(2L)
    }

    @Test
    fun `changed import supersedes the first and keeps history`() {
        feed()
        val first = service("minimal-valid").ingestBlocking("f")
        val second = service("minimal-valid-v2").ingestBlocking("f")

        assertThat(revisions.findById(first.id!!)!!.status).isEqualTo(GtfsRevisionStatus.SUPERSEDED)
        assertThat(revisions.findById(second.id!!)!!.status).isEqualTo(GtfsRevisionStatus.ACTIVE)
        assertThat(revisions.findById(first.id!!) != null).isTrue()
        assertThat(revisions.findById(second.id!!) != null).isTrue()
        assertThat(routes.findByRevisionId(second.id!!)).hasSize(2)
    }
}
