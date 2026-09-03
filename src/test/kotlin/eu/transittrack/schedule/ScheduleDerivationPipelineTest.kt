package eu.transittrack.schedule

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.key
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary

import eu.transittrack.TestcontainersConfiguration
import eu.transittrack.gtfs.download.FeedDownloader
import eu.transittrack.gtfs.feed.FeedInput
import eu.transittrack.gtfs.feed.GtfsFeedRepository
import eu.transittrack.gtfs.feed.GtfsFeedService
import eu.transittrack.gtfs.ingest.IngestionService
import eu.transittrack.gtfs.revision.GtfsRevisionStatus
import eu.transittrack.gtfs.revision.RevisionService
import eu.transittrack.gtfs.support.FixtureDownloader
import eu.transittrack.schedule.derive.DerivationContext
import eu.transittrack.schedule.model.BlockRepository
import eu.transittrack.schedule.model.TripPatternRepository

@SpringBootTest(classes = [eu.transittrack.Application::class])
@Import(TestcontainersConfiguration::class, ScheduleDerivationPipelineTest.Stub::class)
class ScheduleDerivationPipelineTest(
    @Autowired val ingestion: IngestionService,
    @Autowired val feedService: GtfsFeedService,
    @Autowired val feeds: GtfsFeedRepository,
    @Autowired val revisionService: RevisionService,
    @Autowired val patterns: TripPatternRepository,
    @Autowired val blocks: BlockRepository,
    @Autowired val context: DerivationContext,
) {
    @TestConfiguration(proxyBeanMethods = false)
    class Stub {
        @Bean
        @Primary
        fun dl(): FeedDownloader = FixtureDownloader("schedule-sample")
    }

    @Test
    fun `ingest runs the DERIVING step and activates with derived rows`() {
        feedService.register(FeedInput("p1", "P1", null, "http://x/g.zip", null))
        val rev = ingestion.ingestBlocking("p1")
        assertThat(rev.status).isEqualTo(GtfsRevisionStatus.ACTIVE)
        assertThat(patterns.findByRevisionId(rev.id!!)).hasSize(4)
        assertThat(blocks.findByRevisionId(rev.id!!)).hasSize(2)
        val counts = revisionService.revision(rev.id!!).rowCounts
        assertThat(counts).key("trip_patterns").isEqualTo(4L)
        assertThat(counts).key("travel_times_for_stop_path").isEqualTo(13L) // one per stop_path row
        assertThat(counts).key("gtfs_trip") // gtfs counts preserved
        assertThat(context.size()).isEqualTo(0)
    }
}
