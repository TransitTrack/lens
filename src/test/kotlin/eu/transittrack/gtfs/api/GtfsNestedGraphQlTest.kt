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

import eu.transittrack.TestcontainersConfiguration
import eu.transittrack.gtfs.download.FeedDownloader
import eu.transittrack.gtfs.feed.FeedInput
import eu.transittrack.gtfs.feed.GtfsFeedService
import eu.transittrack.gtfs.ingest.IngestionService
import eu.transittrack.gtfs.support.FixtureDownloader

/**
 * Full-stack exercise of the Task 23 nested `@SchemaMapping` resolvers and the generic `gtfsRecords` long-tail query. Real `Application` context + Testcontainers, a stubbed downloader serving the `full-spec-sample` fixture (minimal-valid plus one row in
 * every long-tail file), one ingest in `@BeforeAll`.
 */
@SpringBootTest(
    classes = [eu.transittrack.Application::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
)
@Import(TestcontainersConfiguration::class, GtfsNestedGraphQlTest.StubDownloaderConfig::class)
@AutoConfigureHttpGraphQlTester
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GtfsNestedGraphQlTest(
    @Autowired val tester: HttpGraphQlTester,
    @Autowired val feedService: GtfsFeedService,
    @Autowired val ingestion: IngestionService,
) {
    @TestConfiguration(proxyBeanMethods = false)
    class StubDownloaderConfig {
        @Bean
        @Primary
        fun stubDownloader(): FeedDownloader = FixtureDownloader("full-spec-sample")
    }

    @BeforeAll
    fun ingestOnce() {
        feedService.register(FeedInput("w", "W", null, "http://x/g.zip", null))
        ingestion.ingestBlocking("w")
    }

    @Test
    fun `nested route to trips to stopTimes to stop`() {
        tester
            .document(
                """
            { gtfsRoute(feedCode:"w", routeId:"R1"){
                agency { agencyId }
                trips { tripId stopTimes { stopSequence stop { stopName } } shape { pointCount } }
            } }
            """,
            ).execute()
            .path("gtfsRoute.agency.agencyId")
            .entity(String::class.java)
            .isEqualTo("A1")
            .path("gtfsRoute.trips[0].tripId")
            .entity(String::class.java)
            .isEqualTo("T1")
            .path("gtfsRoute.trips[0].stopTimes[0].stop.stopName")
            .entity(String::class.java)
            .isEqualTo("First")
            .path("gtfsRoute.trips[0].stopTimes[1].stop.stopName")
            .entity(String::class.java)
            .isEqualTo("Second")
            .path("gtfsRoute.trips[0].shape.pointCount")
            .entity(Int::class.java)
            .isEqualTo(2)
    }

    @Test
    fun `trip route resolver round-trips back to the route`() {
        tester
            .document(
                """{ gtfsTrip(feedCode:"w", tripId:"T1"){ tripId route { routeId routeLongName } } }""",
            ).execute()
            .path("gtfsTrip.route.routeId")
            .entity(String::class.java)
            .isEqualTo("R1")
            .path("gtfsTrip.route.routeLongName")
            .entity(String::class.java)
            .isEqualTo("Line One")
    }

    @Test
    fun `stop childStops and level resolvers`() {
        tester
            .document(
                """
            { gtfsStop(feedCode:"w", stopId:"S1"){
                stopId
                level { levelId levelName }
                childStops { stopId parentStation }
            } }
            """,
            ).execute()
            .path("gtfsStop.level.levelId")
            .entity(String::class.java)
            .isEqualTo("L1")
            .path("gtfsStop.childStops[0].stopId")
            .entity(String::class.java)
            .isEqualTo("S3")
            .path("gtfsStop.childStops[0].parentStation")
            .entity(String::class.java)
            .isEqualTo("S1")
    }

    @Test
    fun `gtfsRecords returns long-tail rows as JSON`() {
        tester
            .document(
                """{ gtfsRecords(feedCode:"w", table: FARE_PRODUCTS) }""",
            ).execute()
            .path("gtfsRecords")
            .entityList(Map::class.java)
            .hasSizeGreaterThan(0)
    }

    @Test
    fun `gtfsRecords works for a second long-tail table`() {
        tester
            .document(
                """{ gtfsRecords(feedCode:"w", table: TRANSLATIONS) }""",
            ).execute()
            .path("gtfsRecords")
            .entityList(Map::class.java)
            .hasSizeGreaterThan(0)
    }

    @Test
    fun `gtfsRecords maps the plural enum to the singular table`() {
        tester
            .document(
                """{ gtfsRecords(feedCode:"w", table: ROUTE_NETWORKS) }""",
            ).execute()
            .path("gtfsRecords[0].route_id")
            .entity(String::class.java)
            .isEqualTo("R1")
    }
}
