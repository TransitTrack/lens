package eu.transittrack.gtfs.api

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

import eu.transittrack.config.GraphQlConfiguration
import eu.transittrack.gtfs.draft.DraftEditRepository
import eu.transittrack.gtfs.draft.DraftKind
import eu.transittrack.gtfs.draft.DraftLock
import eu.transittrack.gtfs.draft.DraftService
import eu.transittrack.gtfs.feed.FeedSource
import eu.transittrack.gtfs.feed.GtfsFeed
import eu.transittrack.gtfs.feed.GtfsFeedRepository
import eu.transittrack.gtfs.revision.GtfsRevision
import eu.transittrack.gtfs.revision.GtfsRevisionRepository
import eu.transittrack.gtfs.revision.GtfsRevisionStatus

@GraphQlTest
@Import(
    GraphQlConfiguration::class,
    DraftController::class,
    DraftMapper::class,
    GtfsDtoMapper::class,
)
class DraftGraphQlTest(
    @Autowired val tester: GraphQlTester,
) {
    @MockitoBean lateinit var draftService: DraftService

    @MockitoBean lateinit var editRepo: DraftEditRepository

    @MockitoBean lateinit var feeds: GtfsFeedRepository

    @MockitoBean lateinit var revisions: GtfsRevisionRepository

    private fun draftRev(id: Long) =
        GtfsRevision(feedId = 1, status = GtfsRevisionStatus.DRAFT, sourceUrl = "u").apply {
            this.id = id
            kind = DraftKind.DRAFT
            label = "P1"
            version = 0
            createdAt = Instant.now()
        }

    @Test
    fun `forkDraft returns the new draft`() {
        val feed =
            GtfsFeed(
                "stpt",
                "F",
                null,
                "u",
                null,
                true,
                null,
                FeedSource.CONFIG,
                Instant.now(),
                Instant.now(),
            ).apply { id = 1 }
        whenever(feeds.findById(1L)).thenReturn(Optional.of(feed))
        whenever(draftService.fork("stpt", null, "P1", "alice")).thenReturn(draftRev(42))
        whenever(draftService.currentLock(any())).thenReturn(null)

        tester
            .document(
                """mutation { forkDraft(input:{feedCode:"stpt", label:"P1", editor:"alice"}) { id label status version } }""",
            ).execute()
            .path("forkDraft.id")
            .entity(String::class.java)
            .isEqualTo("42")
            .path("forkDraft.status")
            .entity(String::class.java)
            .isEqualTo("DRAFT")
    }

    @Test
    fun `claimDraftEditor returns the lock`() {
        whenever(draftService.claimEditor(42, "alice", false))
            .thenReturn(DraftLock("alice", Instant.parse("2026-09-06T10:15:00Z")))
        tester
            .document("""mutation { claimDraftEditor(id:"42", editor:"alice") { editor expiresAt } }""")
            .execute()
            .path("claimDraftEditor.editor")
            .entity(String::class.java)
            .isEqualTo("alice")
    }
}
