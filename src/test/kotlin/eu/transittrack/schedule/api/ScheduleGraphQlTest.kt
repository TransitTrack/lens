package eu.transittrack.schedule.api

import eu.transittrack.explorer.TestcontainersConfiguration
import eu.transittrack.gtfs.download.FeedDownloader
import eu.transittrack.gtfs.feed.FeedInput
import eu.transittrack.gtfs.feed.GtfsFeedService
import eu.transittrack.gtfs.ingest.IngestionService
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

@SpringBootTest(
    classes = [eu.transittrack.explorer.Application::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
)
@Import(TestcontainersConfiguration::class, ScheduleGraphQlTest.Stub::class)
@AutoConfigureHttpGraphQlTester
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ScheduleGraphQlTest(
    @Autowired val tester: HttpGraphQlTester,
    @Autowired val feedService: GtfsFeedService,
    @Autowired val ingestion: IngestionService,
) {
    @TestConfiguration(proxyBeanMethods = false)
    class Stub {
        @Bean @Primary fun dl(): FeedDownloader = FixtureDownloader("schedule-sample")
    }

    @BeforeAll
    fun ingestOnce() {
        feedService.register(FeedInput("g", "G", null, "http://x/g.zip", null))
        ingestion.ingestBlocking("g")
    }

    @Test fun `trip patterns for a route with nested stop paths`() {
        tester.document(
            """{ tripPatterns(feedCode:"g", routeId:"RA"){ patternKey stopCount stopPaths { stopPathIndex stopId lengthM } } }""",
        ).execute()
            .path("tripPatterns").entityList(Any::class.java).hasSizeGreaterThan(0)
            .path("tripPatterns[0].stopPaths[0].stopPathIndex").entity(Int::class.java).isEqualTo(0)
    }

    @Test fun `sched trip resolves pattern, block, and schedule times`() {
        tester.document(
            """{ schedTrip(feedCode:"g", tripId:"T1"){ tripId frequencyBased block { blockId tripCount } pattern { patternKey } scheduleTimes { stopPathIndex arrivalSec } } }""",
        ).execute()
            .path("schedTrip.block.blockId").entity(String::class.java).isEqualTo("B1")
            .path("schedTrip.scheduleTimes").entityList(Any::class.java).hasSizeGreaterThan(0)
    }

    @Test fun `block trips are ordered by list index`() {
        tester.document(
            """{ block(feedCode:"g", blockId:"B1", serviceId:"WK"){
                 trips { tripId }
                 blockTrips { listIndex trip { tripId } }
               } }""",
        )
            .execute()
            .path("block.blockTrips[0].listIndex").entity(Int::class.java).isEqualTo(0)
            .path("block.blockTrips[1].listIndex").entity(Int::class.java).isEqualTo(1)
            .path("block.trips[0].tripId").entity(String::class.java).isEqualTo("T1")
    }

    @Test fun `blocks on a date filters by service`() {
        tester.document("""{ blocksOnDate(feedCode:"g", date:"2026-01-05"){ blockId } }""")
            .execute()
            .path("blocksOnDate").entityList(Any::class.java).hasSize(2)
        tester.document("""{ blocksOnDate(feedCode:"g", date:"2026-01-06"){ blockId } }""")
            .execute()
            .path("blocksOnDate").entityList(Any::class.java).hasSize(0)
    }

    @Test fun `gtfs route exposes trip patterns and gtfs trip exposes sched trip`() {
        tester.document("""{ gtfsRoute(feedCode:"g", routeId:"RA"){ routeId tripPatterns { patternKey } } }""")
            .execute()
            .path("gtfsRoute.tripPatterns").entityList(Any::class.java).hasSizeGreaterThan(0)
        tester.document("""{ gtfsTrip(feedCode:"g", tripId:"T1"){ tripId schedTrip { startTimeSec } } }""")
            .execute()
            .path("gtfsTrip.schedTrip.startTimeSec").entity(Int::class.java).isEqualTo(28800)
    }
}
