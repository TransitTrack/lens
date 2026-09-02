package eu.transittrack.schedule

import eu.transittrack.explorer.TestcontainersConfiguration
import eu.transittrack.gtfs.download.FeedDownloader
import eu.transittrack.gtfs.ingest.IngestionService
import eu.transittrack.gtfs.revision.GtfsRevisionStatus
import eu.transittrack.gtfs.feed.FeedInput
import eu.transittrack.gtfs.feed.GtfsFeedService
import eu.transittrack.gtfs.support.FixtureDownloader
import eu.transittrack.schedule.model.BlockRepository
import eu.transittrack.schedule.model.TripPatternRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import eu.transittrack.gtfs.feed.GtfsFeedRepository
import eu.transittrack.gtfs.revision.RevisionService
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest(classes = [eu.transittrack.explorer.Application::class])
@Import(TestcontainersConfiguration::class, ScheduleDerivationPipelineTest.Stub::class)
class ScheduleDerivationPipelineTest(
    @Autowired val ingestion: IngestionService,
    @Autowired val feedService: GtfsFeedService,
    @Autowired val feeds: GtfsFeedRepository,
    @Autowired val revisionService: RevisionService,
    @Autowired val patterns: TripPatternRepository,
    @Autowired val blocks: BlockRepository,
) {
    @TestConfiguration(proxyBeanMethods = false)
    class Stub {
        @Bean @Primary fun dl(): FeedDownloader = FixtureDownloader("schedule-sample")
    }

    @Test
    fun `ingest runs the DERIVING step and activates with derived rows`() {
        feedService.register(FeedInput("p1", "P1", null, "http://x/g.zip", null))
        val rev = ingestion.ingestBlocking("p1")
        assertEquals(GtfsRevisionStatus.ACTIVE, rev.status)
        assertEquals(4, patterns.findByRevisionId(rev.id!!).size)
        assertEquals(2, blocks.findByRevisionId(rev.id!!).size)
        val counts = revisionService.revision(rev.id!!).rowCounts
        assertEquals(4L, counts["trip_patterns"])
        assertTrue(counts.containsKey("gtfs_trip"))   // gtfs counts preserved
    }
}
