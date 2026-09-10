package eu.transittrack.avl.match.cache

import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isSameInstanceAs
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
@Import(TestcontainersConfiguration::class, CachedPatternGeometryReaderTest.Stub::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CachedPatternGeometryReaderTest(
    @Autowired val reader: CachedPatternGeometryReader,
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
    lateinit var stopPathRepo: StopPathRepository

    private var revisionId: Long = 0
    private var patternId: Long = 0

    @BeforeAll
    fun ingest() {
        feedService.register(FeedInput("g", "G", null, "http://x/g.zip", null))
        ingestion.ingestBlocking("g")
        revisionId = revisions.activeRevisionId(feeds.findByCode("g")!!.id!!)!!
        patternId = trips.findByTripId(revisionId, "T1")!!.tripPatternId!!
    }

    @BeforeEach
    fun clear() = clearAvlCaches(cacheManager)

    @Test
    fun `geometry is assembled once and served from cache thereafter`() {
        val first = reader.geometry(revisionId, patternId)
        val second = reader.geometry(revisionId, patternId)

        assertThat(first).isNotNull()
        assertThat(second!!).isSameInstanceAs(first!!)
        verify(stopPathRepo, times(1)).findByTripPatternOrdered(revisionId, patternId)
    }

    @Test
    fun `unknown pattern caches a null result`() {
        assertThat(reader.geometry(revisionId, -1L)).isNull()
        assertThat(reader.geometry(revisionId, -1L)).isNull()
        verify(stopPathRepo, times(1)).findByTripPatternOrdered(revisionId, -1L)
    }
}
