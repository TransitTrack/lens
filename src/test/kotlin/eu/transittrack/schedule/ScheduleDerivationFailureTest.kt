package eu.transittrack.schedule

import java.time.Instant
import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import org.junit.jupiter.api.AfterEach
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
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
import eu.transittrack.gtfs.ingest.IngestionService
import eu.transittrack.gtfs.model.ShapeRepository
import eu.transittrack.gtfs.revision.GtfsRevisionRepository
import eu.transittrack.gtfs.revision.GtfsRevisionStatus
import eu.transittrack.gtfs.revision.RevisionService
import eu.transittrack.gtfs.store.RevisionWriter
import eu.transittrack.gtfs.store.StatelessSessionRevisionWriter
import eu.transittrack.gtfs.support.FixtureDownloader
import eu.transittrack.gtfs.support.PostgresSliceTest
import eu.transittrack.gtfs.validate.GtfsFeedLoader
import eu.transittrack.schedule.derive.ScheduleDerivationService
import eu.transittrack.schedule.derive.ScheduleWriter

@PostgresSliceTest
@EnableConfigurationProperties(GtfsProperties::class)
@Import(
    StatelessSessionRevisionWriter::class,
    RevisionService::class,
    GtfsFeedService::class,
    ScheduleWriter::class,
)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ScheduleDerivationFailureTest(
    @Autowired val feeds: GtfsFeedRepository,
    @Autowired val revisions: GtfsRevisionRepository,
    @Autowired val revisionService: RevisionService,
    @Autowired val feedService: GtfsFeedService,
    @Autowired val gtfsWriter: RevisionWriter,
    @Autowired val gtfsProps: GtfsProperties,
    @Autowired val shapes: ShapeRepository,
    @Autowired val scheduleWriter: ScheduleWriter,
) {
    private val created = mutableListOf<Long>()

    @AfterEach
    fun cleanup() {
        for (id in created) {
            revisions.findByFeedNewestFirst(id).forEach {
                gtfsWriter.deleteAllForRevision(it.id!!)
                revisions.delete(it)
            }
            feeds.deleteById(id)
        }
        created.clear()
    }

    private fun feed(): GtfsFeed =
        feeds
            .save(
                GtfsFeed(
                    "sf",
                    "SF",
                    null,
                    "http://x/g.zip",
                    null,
                    true,
                    true,
                    FeedSource.API,
                    Instant.now(),
                    Instant.now(),
                ),
            ).also { created += it.id!! }

    private fun ingestion(
        derivation: ScheduleDerivationService,
        props: ScheduleProperties,
    ) = IngestionService(
        FixtureDownloader("minimal-valid"),
        revisionService,
        gtfsWriter,
        feeds,
        feedService,
        gtfsProps,
        shapes,
        GtfsFeedLoader(),
        SyncTaskExecutor(),
        mock<org.springframework.context.ApplicationEventPublisher>(),
        eu.transittrack.gtfs.support.postProcessors(
            *(if (props.enabled) arrayOf(derivation) else emptyArray()),
        ),
    )

    @Test
    fun `derivation exception fails the revision`() {
        feed()
        val boom = mock<ScheduleDerivationService>()
        whenever(boom.postProcess(org.mockito.kotlin.any())).doThrow(RuntimeException("boom"))
        val rev = ingestion(boom, ScheduleProperties(enabled = true)).ingestBlocking("sf")
        val loaded = revisions.findById(rev.id!!).get()
        assertThat(loaded.status).isEqualTo(GtfsRevisionStatus.FAILED)
        assertThat(loaded.errorMessage).isNotNull().contains("boom")
    }

    @Test
    fun `disabled schedule derivation activates without calling derive`() {
        feed()
        val never = mock<ScheduleDerivationService>()
        val rev = ingestion(never, ScheduleProperties(enabled = false)).ingestBlocking("sf")
        assertThat(revisions.findById(rev.id!!).get().status).isEqualTo(GtfsRevisionStatus.ACTIVE)
        org.mockito.kotlin
            .verify(never, org.mockito.kotlin.never())
            .postProcess(org.mockito.kotlin.any())
    }
}
