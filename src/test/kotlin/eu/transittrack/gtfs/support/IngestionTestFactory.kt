package eu.transittrack.gtfs.support

import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.test.context.TestComponent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.core.task.SyncTaskExecutor

import eu.transittrack.gtfs.GtfsProperties
import eu.transittrack.gtfs.feed.FeedInput
import eu.transittrack.gtfs.feed.GtfsFeedRepository
import eu.transittrack.gtfs.feed.GtfsFeedService
import eu.transittrack.gtfs.ingest.IngestionPostProcessor
import eu.transittrack.gtfs.ingest.IngestionService
import eu.transittrack.gtfs.model.ShapeRepository
import eu.transittrack.gtfs.revision.RevisionService
import eu.transittrack.gtfs.store.RevisionWriter
import eu.transittrack.gtfs.validate.GtfsFeedLoader
import eu.transittrack.schedule.derive.DerivationService

/**
 * Shared full-context test helper: registers a feed for a fixture folder under
 * `src/test/resources/gtfs/<fixture>/`, runs the real ingestion pipeline (post-processors and all)
 * against it via a [FixtureDownloader], and returns the feed code plus the resulting revision id.
 *
 * Import it into a `@SpringBootTest` alongside `TestcontainersConfiguration`.
 */
@TestComponent
class IngestionTestFactory(
    private val revisionService: RevisionService,
    private val writer: RevisionWriter,
    private val feeds: GtfsFeedRepository,
    private val feedService: GtfsFeedService,
    private val props: GtfsProperties,
    private val shapes: ShapeRepository,
    private val eventPublisher: ApplicationEventPublisher,
    private val derivationService: DerivationService,
    private val postProcessors: ObjectProvider<IngestionPostProcessor>,
) {
    /**
     * Ingests [fixture] (also used as the feed code) blocking and returns `(feedCode, revisionId)`.
     * The feed is registered on first use.
     */
    fun ingest(fixture: String): Pair<String, Long> {
        if (feeds.findByCode(fixture) == null) {
            feedService.register(FeedInput(fixture, fixture.uppercase(), null, "http://x/g.zip", null))
        }
        val service =
            IngestionService(
                FixtureDownloader(fixture),
                revisionService,
                writer,
                feeds,
                feedService,
                props,
                shapes,
                GtfsFeedLoader(),
                SyncTaskExecutor(),
                eventPublisher,
                derivationService,
                postProcessors,
            )
        val rev = service.ingestBlocking(fixture)
        return fixture to rev.id!!
    }
}
