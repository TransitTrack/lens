package eu.transittrack.gtfs.support

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.test.context.TestComponent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.core.task.SyncTaskExecutor

import eu.transittrack.GtfsProperties
import eu.transittrack.gtfs.download.DownloadedFeed
import eu.transittrack.gtfs.download.FeedDownloader
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
 * Import it into a `@SpringBootTest` whose class extends
 * `eu.transittrack.support.PostgresPerMethodTest` or `PostgresPerClassTest`.
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
        val rev = serviceWith(FixtureDownloader(fixture)).ingestBlocking(fixture)
        return fixture to rev.id!!
    }

    /**
     * Registers a feed pointing at [zipPath] (as a `file:` URL) and runs the real ingestion
     * pipeline against that zip, returning the active revision id. Used to round-trip an
     * exported GTFS archive back through ingestion.
     */
    fun ingestFromZip(zipPath: Path): Long {
        val code = "zip-" + zipPath.fileName.toString().substringBeforeLast('.')
        if (feeds.findByCode(code) == null) {
            feedService.register(FeedInput(code, code.uppercase(), null, zipPath.toUri().toString(), null))
        }
        val downloader =
            object : FeedDownloader {
                override fun download(
                    url: String,
                    into: Path,
                ): DownloadedFeed {
                    Files.copy(zipPath, into, StandardCopyOption.REPLACE_EXISTING)
                    val bytes = Files.readAllBytes(into)
                    val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
                    return DownloadedFeed(into, sha, bytes.size.toLong())
                }
            }
        return serviceWith(downloader).ingestBlocking(code).id!!
    }

    private fun serviceWith(downloader: FeedDownloader) =
        IngestionService(
            downloader,
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
}
