package eu.transittrack.gtfs.api

import java.time.Instant
import kotlin.test.Test

import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.graphql.test.autoconfigure.GraphQlTest
import org.springframework.context.annotation.Import
import org.springframework.graphql.test.tester.GraphQlTester
import org.springframework.test.context.bean.override.mockito.MockitoBean

import eu.transittrack.gtfs.draft.DraftEdit
import eu.transittrack.gtfs.draft.DraftJobService
import eu.transittrack.gtfs.draft.DraftKind
import eu.transittrack.gtfs.draft.DraftLock
import eu.transittrack.gtfs.draft.DraftService
import eu.transittrack.gtfs.draft.edit.DraftEditService
import eu.transittrack.gtfs.feed.FeedSource
import eu.transittrack.gtfs.feed.GtfsFeed
import eu.transittrack.gtfs.feed.GtfsFeedService
import eu.transittrack.gtfs.revision.GtfsRevision
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

    @MockitoBean lateinit var editService: DraftEditService

    @MockitoBean lateinit var jobs: DraftJobService

    @MockitoBean lateinit var feedService: GtfsFeedService

    private fun feed() =
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

    private fun draftRev(id: Long) =
        GtfsRevision(feedId = 1, status = GtfsRevisionStatus.DRAFT, sourceUrl = "u").apply {
            this.id = id
            kind = DraftKind.DRAFT
            label = "P1"
            version = 0
            createdAt = Instant.now()
        }

    private fun edit(seq: Int) =
        DraftEdit(
            revisionId = 42,
            seq = seq,
            op = "op$seq",
            summary = "s$seq",
            forward = "{}",
            inverse = "{}",
            appliedAt = Instant.now(),
        )

    @Test
    fun `forkDraft returns the new draft with the claimed lock`() {
        val expiresAt = Instant.parse("2026-09-06T10:15:00Z")
        val claimed =
            draftRev(42).apply {
                editorClaimBy = "alice"
                editorClaimExpiresAt = expiresAt
            }
        whenever(feedService.codeOf(1L)).thenReturn(feed().code)
        whenever(draftService.fork("stpt", null, "P1", "alice")).thenReturn(draftRev(42))
        whenever(draftService.get(42L)).thenReturn(claimed)
        whenever(draftService.currentLock(any())).thenReturn(DraftLock("alice", expiresAt))

        tester
            .document(
                """mutation { forkDraft(input:{feedCode:"stpt", label:"P1", editor:"alice"}) { id label status version lock { editor } } }""",
            ).execute()
            .path("forkDraft.id")
            .entity(String::class.java)
            .isEqualTo("42")
            .path("forkDraft.status")
            .entity(String::class.java)
            .isEqualTo("DRAFT")
            .path("forkDraft.lock.editor")
            .entity(String::class.java)
            .isEqualTo("alice")
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

    @Test
    fun `draftEdits applies the limit via takeLast`() {
        whenever(editService.editsFor(42L))
            .thenReturn((1..5).map(::edit))
        tester
            .document("""{ draftEdits(id:"42", limit:2) { seq } }""")
            .execute()
            .path("draftEdits")
            .entityList(Any::class.java)
            .hasSize(2)
            .path("draftEdits[0].seq")
            .entity(Int::class.java)
            .isEqualTo(4)
    }

    @Test
    fun `activateDraft maps the activated revision through GtfsDtoMapper`() {
        val active =
            GtfsRevision(feedId = 1, status = GtfsRevisionStatus.ACTIVE, sourceUrl = "u").apply {
                id = 42
                createdAt = Instant.now()
            }
        whenever(draftService.activate(42L, "alice", true)).thenReturn(active)
        whenever(feedService.codeOf(1L)).thenReturn(feed().code)
        tester
            .document("""mutation { activateDraft(id:"42", editor:"alice", force:true) { id status feedCode } }""")
            .execute()
            .path("activateDraft.status")
            .entity(String::class.java)
            .isEqualTo("ACTIVE")
            .path("activateDraft.feedCode")
            .entity(String::class.java)
            .isEqualTo("stpt")
    }
}
