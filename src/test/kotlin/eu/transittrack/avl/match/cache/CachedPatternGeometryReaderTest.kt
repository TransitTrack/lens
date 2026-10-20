package eu.transittrack.avl.match.cache

import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isSameInstanceAs
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.cache.CacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import tools.jackson.databind.json.JsonMapper

import eu.transittrack.gtfs.download.FeedDownloader
import eu.transittrack.gtfs.feed.FeedInput
import eu.transittrack.gtfs.feed.GtfsFeedRepository
import eu.transittrack.gtfs.feed.GtfsFeedService
import eu.transittrack.gtfs.ingest.IngestionService
import eu.transittrack.gtfs.model.TripRepository
import eu.transittrack.gtfs.revision.RevisionService
import eu.transittrack.gtfs.support.FixtureDownloader
import eu.transittrack.schedule.model.StopPath
import eu.transittrack.schedule.model.StopPathRepository
import eu.transittrack.support.PostgresPerClassTest

@SpringBootTest(classes = [eu.transittrack.Application::class])
@Import(CachedPatternGeometryReaderTest.Stub::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CachedPatternGeometryReaderTest(
    @Autowired val reader: CachedPatternGeometryReader,
    @Autowired val trips: TripRepository,
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

    @MockitoSpyBean
    lateinit var stopPathRepo: StopPathRepository

    private var revisionId: Long = 0
    private var patternId: Long = 0

    @BeforeAll
    fun ingest() {
        truncateBeforeFixture()
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

    /**
     * No Spring context needed: this exercises [CachedPatternGeometryReader.geometry]'s
     * malformed-shape fallback directly against a stubbed [CachedStopPathReader], independent of
     * the DB-backed fixture used above.
     *
     * `path_geometry` is feed-controlled, not a value this codebase fully controls. A stop path
     * whose coordinates are all identical collapses to a single point after this reader's own
     * consecutive-duplicate dedup, so it hits the pre-existing `pts.size < 2` guard today — the
     * same null outcome the `runCatching` around `ProjectedLine(pts)` exists to guarantee for any
     * future degenerate shape that instead reaches, and throws from, that construction (per Task
     * 2, `ProjectedLine` itself already tolerates duplicate-coordinate input and does not throw
     * for this specific case — see `ProjectedLineTest`). Either way, [geometry] must return `null`
     * rather than let an exception escape this `@Cacheable` method.
     */
    @Test
    fun `stop path with only identical coordinates yields a null geometry, not a thrown exception`() {
        val stubStopPaths = mock<CachedStopPathReader>()
        val degenerate =
            StopPath(
                revisionId = 1L,
                tripPatternId = 1L,
                stopPathIndex = 0,
                stopId = "S1",
                stopSeq = 1,
                lengthM = 0.0,
                pathGeometry = "[[17.0,51.1],[17.0,51.1],[17.0,51.1]]",
                pickupType = null,
                dropOffType = null,
                waitStop = true,
                scheduleAdherenceStop = true,
                layoverStop = false,
                breakTimeSec = null,
            )
        whenever(stubStopPaths.orderedByPattern(1L, 1L)).thenReturn(listOf(degenerate))

        val isolatedReader = CachedPatternGeometryReader(stubStopPaths, JsonMapper.builder().build())

        assertThat(isolatedReader.geometry(1L, 1L)).isNull()
    }
}
