package eu.transittrack.avl.match.cache

import kotlin.test.Test

import assertk.assertThat
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
import eu.transittrack.schedule.model.ScheduleTimeRepository

@SpringBootTest(classes = [eu.transittrack.Application::class])
@Import(TestcontainersConfiguration::class, CachedScheduleReaderTest.Stub::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CachedScheduleReaderTest(
    @Autowired val reader: CachedScheduleReader,
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
    lateinit var repo: ScheduleTimeRepository

    private var revisionId: Long = 0
    private var tripRowId: Long = 0

    @BeforeAll
    fun ingest() {
        feedService.register(FeedInput("g", "G", null, "http://x/g.zip", null))
        ingestion.ingestBlocking("g")
        revisionId = revisions.activeRevisionId(feeds.findByCode("g")!!.id!!)!!
        tripRowId = trips.findByTripId(revisionId, "T1")!!.id!!
    }

    @BeforeEach
    fun clear() = clearAvlCaches(cacheManager)

    @Test
    fun `schedule points are mapped once and cached`() {
        val first = reader.orderedByTrip(revisionId, tripRowId)
        reader.orderedByTrip(revisionId, tripRowId)
        assertThat(first).isNotEmpty()
        verify(repo, times(1)).findByTripOrdered(revisionId, tripRowId)
    }
}
