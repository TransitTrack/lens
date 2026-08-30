package eu.transittrack.gtfs

import eu.transittrack.explorer.TestcontainersConfiguration
import eu.transittrack.gtfs.download.FeedDownloader
import eu.transittrack.gtfs.ingest.IngestionService
import eu.transittrack.gtfs.revision.GtfsRevisionStatus
import eu.transittrack.gtfs.support.FixtureDownloader
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.graphql.test.autoconfigure.tester.AutoConfigureHttpGraphQlTester
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.graphql.test.tester.HttpGraphQlTester
import kotlin.test.assertEquals

/**
 * Whole-subsystem acceptance test: a real [eu.transittrack.explorer.Application] context
 * (pinned via `classes` so the `GtfsSliceTestApplication` is not picked up first),
 * Testcontainers Postgres + LGTM, a stubbed [FeedDownloader] serving the
 * `full-spec-sample` fixture (~32 files). The `e2e` feed is defined purely through
 * `transittrack.gtfs.feeds[0]` config — `GtfsFeedConfigSynchronizer` upserts it into
 * `gtfs_feed` with `source = CONFIG` at startup. The test then ingests it, reads it
 * back over HTTP GraphQL, and confirms a byte-identical re-ingest is `UNCHANGED`.
 */
@SpringBootTest(
    classes = [eu.transittrack.explorer.Application::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "transittrack.gtfs.feeds[0].code=e2e",
        "transittrack.gtfs.feeds[0].name=E2E",
        "transittrack.gtfs.feeds[0].url=http://x/g.zip",
    ],
)
@Import(TestcontainersConfiguration::class, GtfsEndToEndTest.Stub::class)
@AutoConfigureHttpGraphQlTester
class GtfsEndToEndTest(
    @Autowired val tester: HttpGraphQlTester,
    @Autowired val ingestion: IngestionService,
) {

    @TestConfiguration(proxyBeanMethods = false)
    class Stub {
        @Bean
        @Primary
        fun downloader(): FeedDownloader = FixtureDownloader("full-spec-sample")
    }

    @Test
    fun `config feed ingests and serves via GraphQL, second ingest is unchanged`() {
        // The config synchronizer already created the `e2e` feed at startup.
        ingestion.ingestBlocking("e2e")

        tester.document("{ gtfsFeed(code:\"e2e\"){ source activeRevision { status rowCounts } } }")
            .execute()
            .path("gtfsFeed.source").entity(String::class.java).isEqualTo("CONFIG")
            .path("gtfsFeed.activeRevision.status").entity(String::class.java).isEqualTo("ACTIVE")

        tester.document(
            "{ gtfsStopTimes(feedCode:\"e2e\", tripId:\"T1\"){ arrivalTime stop { stopName } } }",
        )
            .execute()
            .path("gtfsStopTimes[0].arrivalTime").entity(String::class.java).isEqualTo("08:00:00")
            .path("gtfsStopTimes[0].stop.stopName").entity(String::class.java).isEqualTo("First")

        val second = ingestion.ingestBlocking("e2e")
        assertEquals(GtfsRevisionStatus.UNCHANGED, second.status)
    }
}
