package eu.transittrack.gtfs.revision

import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.cache.CacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary

import eu.transittrack.avl.match.cache.AvlCaches
import eu.transittrack.gtfs.download.FeedDownloader
import eu.transittrack.gtfs.feed.FeedInput
import eu.transittrack.gtfs.feed.GtfsFeedRepository
import eu.transittrack.gtfs.feed.GtfsFeedService
import eu.transittrack.gtfs.ingest.IngestionService
import eu.transittrack.gtfs.support.FixtureDownloader
import eu.transittrack.support.PostgresPerClassTest

@SpringBootTest(classes = [eu.transittrack.Application::class])
@Import(RevisionActivationCacheEvictionTest.Stub::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RevisionActivationCacheEvictionTest(
    @Autowired val revisions: RevisionService,
    @Autowired val feeds: GtfsFeedRepository,
    @Autowired val feedService: GtfsFeedService,
    @Autowired val ingestion: IngestionService,
    @Autowired val cacheManager: CacheManager,
) : PostgresPerClassTest() {
    @TestConfiguration(proxyBeanMethods = false)
    class Stub {
        @Bean
        @Primary
        fun dl(): FeedDownloader = FixtureDownloader("schedule-sample")
    }

    private var activeRevisionId: Long = 0

    @BeforeAll
    fun ingest() {
        truncateBeforeFixture()
        feedService.register(FeedInput("g", "G", null, "http://x/g.zip", null))
        ingestion.ingestBlocking("g")
        activeRevisionId = revisions.activeRevisionId(feeds.findByCode("g")!!.id!!)!!
    }

    @Test
    fun `activate clears the AVL read caches after commit`() {
        val cache = cacheManager.getCache(AvlCaches.SCHEDULE)!!
        cache.put("$activeRevisionId:seed", listOf<Int>())
        assertThat(cache.get("$activeRevisionId:seed")).isNotNull()

        // Re-activate the already-active revision: a no-op swap that still commits and must evict.
        revisions.activate(activeRevisionId)

        assertThat(cache.get("$activeRevisionId:seed")).isNull()
    }
}
