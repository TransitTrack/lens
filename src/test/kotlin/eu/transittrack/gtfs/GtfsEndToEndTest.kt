package eu.transittrack.gtfs

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.graphql.test.autoconfigure.tester.AutoConfigureHttpGraphQlTester
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.graphql.test.tester.HttpGraphQlTester
import org.springframework.graphql.test.tester.entity

import eu.transittrack.gtfs.download.FeedDownloader
import eu.transittrack.gtfs.feed.GtfsFeedRepository
import eu.transittrack.gtfs.ingest.IngestionService
import eu.transittrack.gtfs.revision.GtfsRevisionStatus
import eu.transittrack.gtfs.revision.RevisionService
import eu.transittrack.gtfs.support.FixtureDownloader
import eu.transittrack.support.PostgresPerMethodTest

/**
 * Whole-subsystem acceptance test: a real [eu.transittrack.Application] context (pinned via `classes` so the `GtfsSliceTestApplication` is not picked up first), Testcontainers Postgres + LGTM, a stubbed [FeedDownloader] serving the
 * `full-spec-sample` fixture (~32 files). The `e2e` feed is defined purely through `transittrack.feed.feeds[0]` config — `GtfsFeedConfigSynchronizer` upserts it into `gtfs_feed` with `source = CONFIG` at startup. The test then ingests it, reads it back over
 * HTTP GraphQL, and confirms a byte-identical re-ingest is `UNCHANGED`.
 */
@SpringBootTest(
    classes = [eu.transittrack.Application::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties =
        [
            "transittrack.feed.feeds[0].code=e2e",
            "transittrack.feed.feeds[0].name=E2E",
            "transittrack.feed.feeds[0].url=http://x/g.zip",
        ],
)
@Import(GtfsEndToEndTest.Stub::class)
@AutoConfigureHttpGraphQlTester
class GtfsEndToEndTest(
    @Autowired val tester: HttpGraphQlTester,
    @Autowired val ingestion: IngestionService,
    @Autowired val feeds: GtfsFeedRepository,
    @Autowired val revisionService: RevisionService,
) : PostgresPerMethodTest() {
    /**
     * This class's Spring context config-injects the `e2e` feed and kicks off an async ingest for
     * it at context-startup time ([awaitStartupIngestSettled] below waits for that), so — like the
     * `PostgresPerClassTest` classes whose fixture is built once in `@BeforeAll` — a per-method
     * truncate here would destroy that startup-injected fixture before the (single) `@Test` method
     * ever sees it, and could race the still-in-flight startup ingest's own writes. This class has
     * exactly one `@Test` method, so no cross-method isolation is needed within it; isolation from
     * *other* test classes' leftover data comes from `e2e` being a feed code no other test uses.
     */
    override fun truncateBeforeEachTest() {}

    @TestConfiguration(proxyBeanMethods = false)
    class Stub {
        @Bean
        @Primary
        fun downloader(): FeedDownloader = FixtureDownloader("full-spec-sample")
    }

    /** `GtfsFeedConfigSynchronizer` triggers an ASYNC ingest for the freshly-inserted `e2e` config feed at startup. Wait for that ingest to reach a terminal state before starting our own blocking ingest, otherwise the per-feed in-progress guard rejects it. */
    private fun awaitStartupIngestSettled() {
        val feedId = feeds.findByCode("e2e")!!.id!!
        val deadline = System.nanoTime() + 30_000_000_000L
        while (revisionService.hasInProgress(feedId)) {
            check(System.nanoTime() < deadline) {
                "startup ingest of 'e2e' did not settle within 30s"
            }
            Thread.sleep(100)
        }
    }

    @Test
    fun `config feed ingests and serves via GraphQL, second ingest is unchanged`() {
        // The config synchronizer already created the `e2e` feed and kicked off an
        // async ingest at startup; let it finish before we ingest synchronously.
        awaitStartupIngestSettled()
        ingestion.ingestBlocking("e2e")

        tester
            .document("{ feed(code:\"e2e\"){ source activeRevision { status rowCounts } } }")
            .execute()
            .path(
                "feed.source",
            ).entity<String>()
            .isEqualTo("CONFIG")
            .path("feed.activeRevision.status")
            .entity<String>()
            .isEqualTo("ACTIVE")

        tester
            .document(
                "{ stopTimes(feedCode:\"e2e\", tripId:\"T1\"){ arrivalTime stop { stopName } } }",
            ).execute()
            .path("stopTimes[0].arrivalTime")
            .entity(String::class.java)
            .isEqualTo("08:00:00")
            .path("stopTimes[0].stop.stopName")
            .entity(String::class.java)
            .isEqualTo("First")

        val second = ingestion.ingestBlocking("e2e")
        assertThat(second.status).isEqualTo(GtfsRevisionStatus.UNCHANGED)
    }
}
