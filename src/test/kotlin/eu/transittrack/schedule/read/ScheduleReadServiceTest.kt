package eu.transittrack.schedule.read

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
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
        assertThat(read.tripPatterns("sd", null, null)).hasSize(4)
        assertThat(
            read
                .tripPatterns("sd", "RA", null)
                .map { it.routeId }
                .distinct(),
        ).hasSize(1)
        assertThat(read.block("sd", "B1", "WK", null)!!.tripCount).isEqualTo(2)
        // B1, B2 plus a singleton block per blockless trip (T3, and T5's frequency block) — every
        // trip gets a block (see SchedTripProcessor's blockId fallback).
        assertThat(read.blocksOnDate("sd", "2026-01-05", null)).hasSize(4) // Monday, WK
        assertThat(read.blocksOnDate("sd", "2026-01-06", null)).isEmpty() // WK removed
        assertThat(read.tripsOnDate("sd", "2026-01-05", null, null)).hasSize(5)
        assertThat(
            read
                .tripsOnDate("sd", "2026-01-05", "RA", null)
                .map { it.routeId }
                .distinct(),
        ).isEqualTo(listOf("RA"))
    }
}
