package eu.transittrack.gtfs.api

import eu.transittrack.explorer.TestcontainersConfiguration
import eu.transittrack.gtfs.download.FeedDownloader
import eu.transittrack.gtfs.feed.FeedInput
import eu.transittrack.gtfs.feed.GtfsFeedService
import eu.transittrack.gtfs.feed.GtfsFeedRepository
import eu.transittrack.gtfs.ingest.IngestionService
import eu.transittrack.gtfs.revision.RevisionService
import eu.transittrack.gtfs.support.FixtureDownloader
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.graphql.test.autoconfigure.tester.AutoConfigureHttpGraphQlTester
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.graphql.test.tester.HttpGraphQlTester

/**
 * Full-stack read path: real `Application` context (must be pinned via `classes` so the
 * slice `GtfsSliceTestApplication` is not picked up first), Testcontainers Postgres +
 * LGTM, a stubbed downloader serving the `minimal-valid` fixture, one ingest in
 * `@BeforeAll`, then GraphQL queries over HTTP.
 */
@SpringBootTest(
    classes = [eu.transittrack.explorer.Application::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
)
@Import(TestcontainersConfiguration::class, GtfsQueryGraphQlTest.StubDownloaderConfig::class)
@AutoConfigureHttpGraphQlTester
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GtfsQueryGraphQlTest(
    @Autowired val tester: HttpGraphQlTester,
    @Autowired val feedService: GtfsFeedService,
    @Autowired val ingestion: IngestionService,
    @Autowired val revisionService: RevisionService,
    @Autowired val feeds: GtfsFeedRepository,
) {

    @TestConfiguration(proxyBeanMethods = false)
    class StubDownloaderConfig {
        @Bean
        @Primary
        fun stubDownloader(): FeedDownloader = FixtureDownloader("minimal-valid")
    }

    @BeforeAll
    fun ingestOnce() {
        feedService.register(FeedInput("w", "W", null, "http://x/g.zip", null))
        ingestion.ingestBlocking("w")
    }

    @Test
    fun `routes resolve against the active revision by default`() {
        tester.document("{ gtfsRoutes(feedCode:\"w\"){ routeId routeShortName routeType } }")
            .execute()
            .path("gtfsRoutes[0].routeId").entity(String::class.java).isEqualTo("R1")
            .path("gtfsRoutes[0].routeType").entity(Int::class.java).isEqualTo(3)
    }

    @Test
    fun `single route lookup by id`() {
        tester.document("{ gtfsRoute(feedCode:\"w\", routeId:\"R1\"){ routeId routeLongName } }")
            .execute()
            .path("gtfsRoute.routeLongName").entity(String::class.java).isEqualTo("Line One")
    }

    @Test
    fun `stop_times expose seconds and formatted time`() {
        tester.document(
            "{ gtfsStopTimes(feedCode:\"w\", tripId:\"T1\"){ stopSequence arrivalTime arrivalTimeSeconds } }",
        )
            .execute()
            .path("gtfsStopTimes[0].arrivalTimeSeconds").entity(Int::class.java).isEqualTo(28800)
            .path("gtfsStopTimes[0].arrivalTime").entity(String::class.java).isEqualTo("08:00:00")
    }

    @Test
    fun `trips filter by route`() {
        tester.document("{ gtfsTrips(feedCode:\"w\", routeId:\"R1\"){ tripId serviceId shapeId } }")
            .execute()
            .path("gtfsTrips[0].tripId").entity(String::class.java).isEqualTo("T1")
            .path("gtfsTrips[0].shapeId").entity(String::class.java).isEqualTo("SH1")
    }

    @Test
    fun `shape returns aggregated points`() {
        tester.document(
            "{ gtfsShape(feedCode:\"w\", shapeId:\"SH1\"){ shapeId pointCount points { lat lon sequence } } }",
        )
            .execute()
            .path("gtfsShape.pointCount").entity(Int::class.java).isEqualTo(2)
            .path("gtfsShape.points[1].sequence").entity(Int::class.java).isEqualTo(2)
    }

    @Test
    fun `feed info is exposed`() {
        tester.document("{ gtfsFeedInfo(feedCode:\"w\"){ feedPublisherName feedLang } }")
            .execute()
            .path("gtfsFeedInfo.feedPublisherName").entity(String::class.java).isEqualTo("Test")
    }

    @Test
    fun `explicit revisionId is honoured`() {
        val feedId = feeds.findByCode("w")!!.id!!
        val rev = revisionService.activeRevisionId(feedId).toString()
        tester.document(
            "query(\$r: ID){ gtfsRoutes(feedCode:\"w\", revisionId:\$r){ routeId } }",
        )
            .variable("r", rev)
            .execute()
            .path("gtfsRoutes[0].routeId").entity(String::class.java).isEqualTo("R1")
    }

    @Test
    fun `feed with no active revision errors`() {
        feedService.register(FeedInput("empty", "E", null, "http://x/g.zip", null))
        tester.document("{ gtfsRoutes(feedCode:\"empty\"){ routeId } }")
            .execute()
            .errors()
            .satisfy { assert(it.isNotEmpty()) }
    }
}
