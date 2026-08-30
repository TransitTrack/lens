package eu.transittrack.gtfs.api

import eu.transittrack.gtfs.feed.FeedInput
import eu.transittrack.gtfs.feed.FeedSource
import eu.transittrack.gtfs.feed.GtfsFeed
import eu.transittrack.gtfs.feed.GtfsFeedRepository
import eu.transittrack.gtfs.feed.GtfsFeedService
import eu.transittrack.gtfs.ingest.IngestionService
import eu.transittrack.gtfs.revision.GtfsRevision
import eu.transittrack.gtfs.revision.GtfsRevisionRepository
import eu.transittrack.gtfs.revision.GtfsRevisionStatus
import eu.transittrack.gtfs.revision.RevisionService
import java.time.Instant
import java.util.Optional
import kotlin.test.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.graphql.test.autoconfigure.GraphQlTest
import org.springframework.context.annotation.Import
import org.springframework.graphql.test.tester.GraphQlTester
import org.springframework.test.context.bean.override.mockito.MockitoBean
import eu.transittrack.gtfs.config.GtfsGraphQlConfig

@GraphQlTest
@Import(
    GtfsGraphQlConfig::class,
    GtfsDtoMapper::class,
    GtfsFeedController::class,
    GtfsRevisionController::class,
    GtfsMutationController::class,
)
class GtfsFeedGraphQlTest(@Autowired val tester: GraphQlTester) {

    @MockitoBean lateinit var feedService: GtfsFeedService
    @MockitoBean lateinit var ingestion: IngestionService
    @MockitoBean lateinit var revisionService: RevisionService
    @MockitoBean lateinit var revisions: GtfsRevisionRepository
    @MockitoBean lateinit var feeds: GtfsFeedRepository

    private fun feed() = GtfsFeed(
        "w", "W", null, "http://x/z.zip", null, true, null,
        FeedSource.API, Instant.now(), Instant.now(), null, 1L,
    )

    private fun pendingRevision(id: Long) =
        GtfsRevision(feedId = 1, status = GtfsRevisionStatus.PENDING, sourceUrl = "u").apply { this.id = id }

    @Test
    fun `query gtfsFeed`() {
        whenever(feedService.get("w")).thenReturn(feed())
        whenever(revisions.findByFeedIdOrderByCreatedAtDesc(any())).thenReturn(emptyList())
        tester.document("{ gtfsFeed(code:\"w\"){ code name enabled source } }")
            .execute()
            .path("gtfsFeed.code").entity(String::class.java).isEqualTo("w")
            .path("gtfsFeed.source").entity(String::class.java).isEqualTo("API")
    }

    @Test
    fun `mutation ingestFeed returns pending revision`() {
        whenever(ingestion.ingest("w")).thenReturn(pendingRevision(42))
        tester.document("mutation { ingestFeed(feedCode:\"w\"){ id status feedCode } }")
            .execute()
            .path("ingestFeed.status").entity(String::class.java).isEqualTo("PENDING")
            .path("ingestFeed.id").entity(String::class.java).isEqualTo("42")
    }

    @Test
    fun `mutation registerGtfsFeed returns the created feed`() {
        whenever(feedService.register(any<FeedInput>())).thenReturn(feed())
        tester.document(
            "mutation { registerGtfsFeed(input:{code:\"w\",name:\"W\",url:\"http://x/z.zip\"}){ code enabled } }",
        )
            .execute()
            .path("registerGtfsFeed.code").entity(String::class.java).isEqualTo("w")
    }

    @Test
    fun `query gtfsRevision maps rowCounts JSON and validationReport into a summary`() {
        val feed = feed()
        val revision = GtfsRevision(feedId = 1, status = GtfsRevisionStatus.READY, sourceUrl = "u").apply {
            id = 5
            rowCounts = mapOf("stop" to 12L, "trip" to 3L)
            validationReport =
                """{"issues":[{"rule":"r1","severity":"ERROR","count":2,"sample":"s"},""" +
                """{"rule":"r2","severity":"WARNING","count":4,"sample":"s"}]}"""
        }
        whenever(revisions.findById(5L)).thenReturn(Optional.of(revision))
        whenever(feeds.findById(1L)).thenReturn(Optional.of(feed))
        tester.document(
            "{ gtfsRevision(id:\"5\"){ rowCounts validationSummary { errorCount warningCount } } }",
        )
            .execute()
            .path("gtfsRevision.rowCounts.stop").entity(Long::class.java).isEqualTo(12L)
            .path("gtfsRevision.validationSummary.errorCount").entity(Long::class.java).isEqualTo(2L)
            .path("gtfsRevision.validationSummary.warningCount").entity(Long::class.java).isEqualTo(4L)
    }

    @Test
    fun `mutation deleteRevision refuses an ACTIVE revision`() {
        val active = GtfsRevision(feedId = 1, status = GtfsRevisionStatus.ACTIVE, sourceUrl = "u").apply { id = 7 }
        whenever(revisions.findById(7L)).thenReturn(Optional.of(active))
        tester.document("mutation { deleteRevision(revisionId:\"7\") }")
            .execute()
            .errors()
            .expect { it.message?.contains("ACTIVE") == true }
            .verify()
    }
}
