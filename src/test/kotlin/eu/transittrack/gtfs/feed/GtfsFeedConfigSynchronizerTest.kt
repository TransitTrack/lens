package eu.transittrack.gtfs.feed

import eu.transittrack.gtfs.config.GtfsProperties
import eu.transittrack.gtfs.ingest.IngestionService
import eu.transittrack.gtfs.support.PostgresSliceTest
import org.mockito.Mockito.mock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import

@PostgresSliceTest
@Import(GtfsFeedService::class)
class GtfsFeedConfigSynchronizerTest(@Autowired val repo: GtfsFeedRepository) {
    val ingestionService: IngestionService = mock(IngestionService::class.java)
    private fun sync(pruneConfigFeeds: Boolean = false, vararg feeds: GtfsProperties.FeedDef) =
        GtfsFeedConfigSynchronizer(
            repo,
            GtfsProperties(pruneConfigFeeds = pruneConfigFeeds, feeds = feeds.toList()),
            ingestionService
        ).sync()

    @Test fun `inserts new config feeds and updates existing`() {
        sync(feeds = arrayOf(feed("a", "http://a/1.zip")))
        assertEquals("http://a/1.zip", repo.findByCode("a")!!.url)
        sync(feeds = arrayOf(feed("a", "http://a/2.zip")))
        assertEquals("http://a/2.zip", repo.findByCode("a")!!.url)
        assertEquals(FeedSource.CONFIG, repo.findByCode("a")!!.source)
    }

    @Test fun `fails when config code collides with an API feed`() {
        repo.save(
            GtfsFeed(
                "a", "A", null, "http://x/z.zip", null, true, null,
                FeedSource.API, java.time.Instant.now(), java.time.Instant.now(),
            ),
        )
        assertFailsWith<FeedConflictException> { sync(feeds = arrayOf(feed("a", "http://a/1.zip"))) }
    }

    @Test fun `prunes removed config feeds only when enabled`() {
        sync(feeds = arrayOf(feed("a", "http://a/1.zip"), feed("b", "http://b/1.zip")))
        sync(feeds = arrayOf(feed("a", "http://a/1.zip"))) // b still present
        assertNotNull(repo.findByCode("b"))
        sync(pruneConfigFeeds = true, feeds = arrayOf(feed("a", "http://a/1.zip")))
        assertNull(repo.findByCode("b"))
    }

    private fun feed(code: String, url: String) =
        GtfsProperties.FeedDef(code = code, name = code.uppercase(), url = url)
}
