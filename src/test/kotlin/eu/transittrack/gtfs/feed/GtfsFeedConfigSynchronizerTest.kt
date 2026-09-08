package eu.transittrack.gtfs.feed

import kotlin.test.Test

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import org.mockito.Mockito.mock
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import

import eu.transittrack.FeedsProperties
import eu.transittrack.gtfs.ingest.IngestionService
import eu.transittrack.gtfs.support.PostgresSliceTest

@PostgresSliceTest
@Import(GtfsFeedService::class)
class GtfsFeedConfigSynchronizerTest(
    @Autowired val repo: GtfsFeedRepository,
) {
    val ingestionService: IngestionService = mock(IngestionService::class.java)

    private fun sync(
        pruneConfigFeeds: Boolean = false,
        vararg feeds: FeedsProperties.FeedDef,
    ) = GtfsFeedConfigSynchronizer(
        repo,
        FeedsProperties(pruneConfigFeeds = pruneConfigFeeds, feeds = feeds.toList()),
        ingestionService,
    ).sync()

    @Test
    fun `inserts new config feeds and updates existing`() {
        sync(feeds = arrayOf(feed("a", "http://a/1.zip")))
        assertThat(repo.findByCode("a")!!.url).isEqualTo("http://a/1.zip")
        sync(feeds = arrayOf(feed("a", "http://a/2.zip")))
        assertThat(repo.findByCode("a")!!.url).isEqualTo("http://a/2.zip")
        assertThat(repo.findByCode("a")!!.source).isEqualTo(FeedSource.CONFIG)
    }

    @Test
    fun `fails when config code collides with an API feed`() {
        repo.save(
            GtfsFeed(
                "a",
                "A",
                null,
                "http://x/z.zip",
                null,
                true,
                null,
                FeedSource.API,
                java.time.Instant.now(),
                java.time.Instant.now(),
            ),
        )
        assertFailure {
            sync(feeds = arrayOf(feed("a", "http://a/1.zip")))
        }.isInstanceOf<FeedConflictException>()
    }

    @Test
    fun `prunes removed config feeds only when enabled`() {
        sync(feeds = arrayOf(feed("a", "http://a/1.zip"), feed("b", "http://b/1.zip")))
        sync(feeds = arrayOf(feed("a", "http://a/1.zip"))) // b still present
        assertThat(repo.findByCode("b")).isNotNull()
        sync(pruneConfigFeeds = true, feeds = arrayOf(feed("a", "http://a/1.zip")))
        assertThat(repo.findByCode("b")).isNull()
    }

    private fun feed(
        code: String,
        url: String,
    ) = FeedsProperties.FeedDef(code = code, name = code.uppercase(), url = url)
}
