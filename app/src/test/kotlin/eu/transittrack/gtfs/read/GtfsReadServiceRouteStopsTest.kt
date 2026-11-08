package eu.transittrack.gtfs.read

import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary

import eu.transittrack.gtfs.download.FeedDownloader
import eu.transittrack.gtfs.feed.FeedInput
import eu.transittrack.gtfs.feed.GtfsFeedService
import eu.transittrack.gtfs.ingest.IngestionService
import eu.transittrack.gtfs.support.FixtureDownloader
import eu.transittrack.support.PostgresPerClassTest

@SpringBootTest(classes = [eu.transittrack.Application::class])
@Import(GtfsReadServiceRouteStopsTest.Stub::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GtfsReadServiceRouteStopsTest(
    @Autowired val read: GtfsReadService,
    @Autowired val feedService: GtfsFeedService,
    @Autowired val ingestion: IngestionService,
) : PostgresPerClassTest() {
    @TestConfiguration(proxyBeanMethods = false)
    class Stub {
        @Bean
        @Primary
        fun dl(): FeedDownloader = FixtureDownloader("schedule-sample")
    }

    @BeforeAll
    fun ingest() {
        truncateBeforeFixture()
        feedService.register(FeedInput("g", "G", null, "http://x/g.zip", null))
        ingestion.ingestBlocking("g")
    }

    @Test
    fun `a route's stops are every stop on its patterns, each once`() {
        val stops = read.routeStops("g", "RA", null)

        assertThat(stops).isNotNull()
        val ids = stops!!.map { it.stopId }
        assertThat(ids).containsExactlyInAnyOrder("S1", "S2", "S3", "S4")
        assertThat(ids.distinct().size).isEqualTo(ids.size)
    }

    @Test
    fun `an unknown route has no stops rather than an empty list`() {
        assertThat(read.routeStops("g", "nope", null)).isNull()
    }
}
