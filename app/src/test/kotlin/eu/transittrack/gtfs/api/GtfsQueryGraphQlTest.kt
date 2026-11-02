package eu.transittrack.gtfs.api

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

import eu.transittrack.gtfs.download.FeedDownloader
import eu.transittrack.gtfs.feed.FeedInput
import eu.transittrack.gtfs.feed.GtfsFeedRepository
import eu.transittrack.gtfs.feed.GtfsFeedService
import eu.transittrack.gtfs.ingest.IngestionService
import eu.transittrack.gtfs.revision.RevisionService
import eu.transittrack.gtfs.support.FixtureDownloader
import eu.transittrack.support.PostgresPerClassTest

/**
 * Full-stack read path: real `Application` context (must be pinned via `classes` so the slice `GtfsSliceTestApplication` is not picked up first), Testcontainers Postgres + LGTM, a stubbed downloader serving the `minimal-valid` fixture, one ingest in
 * `@BeforeAll`, then GraphQL queries over HTTP.
 */
@SpringBootTest(
    classes = [eu.transittrack.Application::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
)
@Import(GtfsQueryGraphQlTest.StubDownloaderConfig::class)
@AutoConfigureHttpGraphQlTester
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GtfsQueryGraphQlTest(
    @Autowired val tester: HttpGraphQlTester,
    @Autowired val feedService: GtfsFeedService,
    @Autowired val ingestion: IngestionService,
    @Autowired val revisionService: RevisionService,
    @Autowired val feeds: GtfsFeedRepository,
) : PostgresPerClassTest() {
    @TestConfiguration(proxyBeanMethods = false)
    class StubDownloaderConfig {
        @Bean
        @Primary
        fun stubDownloader(): FeedDownloader = FixtureDownloader("minimal-valid")
    }

    @BeforeAll
    fun ingestOnce() {
        truncateBeforeFixture()
        feedService.register(FeedInput("w", "W", null, "http://x/g.zip", null))
        ingestion.ingestBlocking("w")
    }

    @Test
    fun `routes resolve against the active revision by default`() {
        tester
            .document(
                "{ routes(feedCode:\"w\"){ routeId routeShortName routeType } }",
            ).execute()
            .path(
                "routes[0].routeId",
            ).entity(String::class.java)
            .isEqualTo("R1")
            .path("routes[0].routeType")
            .entity(Int::class.java)
            .isEqualTo(3)
    }

    @Test
    fun `single route lookup by id`() {
        tester
            .document(
                "{ route(feedCode:\"w\", routeId:\"R1\"){ routeId routeLongName } }",
            ).execute()
            .path("route.routeLongName")
            .entity(String::class.java)
            .isEqualTo("Line One")
    }

    @Test
    fun `stop_times expose seconds and formatted time`() {
        tester
            .document(
                "{ stopTimes(feedCode:\"w\", tripId:\"T1\"){ stopSequence arrivalTime arrivalTimeSeconds } }",
            ).execute()
            .path("stopTimes[0].arrivalTimeSeconds")
            .entity(Int::class.java)
            .isEqualTo(28800)
            .path("stopTimes[0].arrivalTime")
            .entity(String::class.java)
            .isEqualTo("08:00:00")
    }

    @Test
    fun `trips filter by route`() {
        tester
            .document(
                "{ trips(feedCode:\"w\", routeId:\"R1\"){ tripId serviceId shapeId } }",
            ).execute()
            .path(
                "trips[0].tripId",
            ).entity(String::class.java)
            .isEqualTo("T1")
            .path("trips[0].shapeId")
            .entity(String::class.java)
            .isEqualTo("SH1")
    }

    @Test
    fun `shape returns aggregated points`() {
        tester
            .document(
                "{ shape(feedCode:\"w\", shapeId:\"SH1\"){ shapeId pointCount points { lat lon sequence } } }",
            ).execute()
            .path("shape.pointCount")
            .entity(Int::class.java)
            .isEqualTo(2)
            .path("shape.points[1].sequence")
            .entity(Int::class.java)
            .isEqualTo(2)
    }

    @Test
    fun `feed info is exposed`() {
        tester
            .document(
                "{ feedInfo(feedCode:\"w\"){ feedPublisherName feedLang } }",
            ).execute()
            .path("feedInfo.feedPublisherName")
            .entity(String::class.java)
            .isEqualTo("Test")
    }

    @Test
    fun `explicit revisionId is honoured`() {
        val feedId = feeds.findByCode("w")!!.id!!
        val rev = revisionService.activeRevisionId(feedId).toString()
        tester
            .document(
                "query(\$r: ID){ routes(feedCode:\"w\", revisionId:\$r){ routeId } }",
            ).variable("r", rev)
            .execute()
            .path("routes[0].routeId")
            .entity(String::class.java)
            .isEqualTo("R1")
    }

    @Test
    fun `feed with no active revision errors`() {
        feedService.register(FeedInput("empty", "E", null, "http://x/g.zip", null))
        tester.document("{ routes(feedCode:\"empty\"){ routeId } }").execute().errors().satisfy {
            assert(it.isNotEmpty())
        }
    }
}
