package eu.transittrack.schedule.read

import kotlin.test.assertEquals

import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary

import eu.transittrack.TestcontainersConfiguration
import eu.transittrack.gtfs.download.FeedDownloader
import eu.transittrack.gtfs.feed.FeedInput
import eu.transittrack.gtfs.feed.GtfsFeedService
import eu.transittrack.gtfs.ingest.IngestionService
import eu.transittrack.gtfs.support.FixtureDownloader

@SpringBootTest(classes = [eu.transittrack.Application::class])
@Import(TestcontainersConfiguration::class, ScheduleReadServiceTest.Stub::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ScheduleReadServiceTest(
    @Autowired val feedService: GtfsFeedService,
    @Autowired val ingestion: IngestionService,
    @Autowired val read: ScheduleReadService,
) {
    @TestConfiguration(proxyBeanMethods = false)
    class Stub {
        @Bean
        @Primary
        fun dl(): FeedDownloader = FixtureDownloader("schedule-sample")
    }

    @BeforeAll
    fun ingestOnce() {
        feedService.register(FeedInput("sd", "SD", null, "http://x/g.zip", null))
        ingestion.ingestBlocking("sd")
    }

    @Test
    fun `reads patterns, trip, block, and blocks-on-date`() {
        assertEquals(4, read.tripPatterns("sd", null, null).size)
        assertEquals(
            1,
            read
                .tripPatterns("sd", "RA", null)
                .map { it.routeId }
                .distinct()
                .size,
        )
        assertEquals("RA", read.schedTrip("sd", "T2", null)!!.routeId)
        assertEquals(2, read.block("sd", "B1", "WK", null)!!.tripCount)
        assertEquals(2, read.blocksOnDate("sd", "2026-01-05", null).size) // Monday, WK
        assertEquals(0, read.blocksOnDate("sd", "2026-01-06", null).size) // WK removed
        assertEquals(5, read.tripsOnDate("sd", "2026-01-05", null, null).size)
    }
}
