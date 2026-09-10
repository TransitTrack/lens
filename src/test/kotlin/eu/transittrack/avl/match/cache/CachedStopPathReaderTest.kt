package eu.transittrack.avl.match.cache

import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEmpty
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.cache.CacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean

import eu.transittrack.TestcontainersConfiguration
import eu.transittrack.gtfs.download.FeedDownloader
import eu.transittrack.gtfs.feed.FeedInput
import eu.transittrack.gtfs.feed.GtfsFeedRepository
import eu.transittrack.gtfs.feed.GtfsFeedService
import eu.transittrack.gtfs.ingest.IngestionService
import eu.transittrack.gtfs.model.TripRepository
import eu.transittrack.gtfs.revision.RevisionService
import eu.transittrack.gtfs.support.FixtureDownloader
import eu.transittrack.schedule.model.StopPathRepository

@SpringBootTest(classes = [eu.transittrack.Application::class])
@Import(TestcontainersConfiguration::class, CachedStopPathReaderTest.Stub::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CachedStopPathReaderTest(
    @Autowired val reader: CachedStopPathReader,
    @Autowired val trips: TripRepository,
    @Autowired val revisions: RevisionService,
    @Autowired val feeds: GtfsFeedRepository,
    @Autowired val feedService: GtfsFeedService,
    @Autowired val ingestion: IngestionService,
    @Autowired val cacheManager: CacheManager,
) {
    @TestConfiguration(proxyBeanMethods = false)
    class Stub {
        @Bean
        @Primary
        fun dl(): FeedDownloader = FixtureDownloader("schedule-sample")
    }

    @MockitoSpyBean
    lateinit var repo: StopPathRepository

    private var revisionId: Long = 0
    private var patternId: Long = 0

    @BeforeAll
    fun ingest() {
        feedService.register(FeedInput("g", "G", null, "http://x/g.zip", null))
        ingestion.ingestBlocking("g")
        val feedId = feeds.findByCode("g")!!.id!!
        revisionId = revisions.activeRevisionId(feedId)!!
        patternId = trips.findByTripId(revisionId, "T1")!!.tripPatternId!!
    }

    @BeforeEach
    fun clear() = clearAvlCaches(cacheManager)

    @Test
    fun `second call for the same pattern does not hit the repository`() {
        val first = reader.orderedByPattern(revisionId, patternId)
        val second = reader.orderedByPattern(revisionId, patternId)

        assertThat(first).isNotEmpty()
        assertThat(second.map { it.stopPathIndex }).isEqualTo(first.map { it.stopPathIndex })
        verify(repo, times(1)).findByTripPatternOrdered(revisionId, patternId)
    }

    @Test
    fun `a different revision id is a separate cache entry`() {
        reader.orderedByPattern(revisionId, patternId)
        reader.orderedByPattern(revisionId + 999_999, patternId)

        verify(repo, times(1)).findByTripPatternOrdered(revisionId, patternId)
        verify(repo, times(1)).findByTripPatternOrdered(revisionId + 999_999, patternId)
    }
}
