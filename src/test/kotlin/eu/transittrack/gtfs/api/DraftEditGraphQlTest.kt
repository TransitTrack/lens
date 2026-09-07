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

import eu.transittrack.config.GraphQlConfiguration
import eu.transittrack.gtfs.draft.DraftEdit
import eu.transittrack.gtfs.draft.DraftService
import eu.transittrack.gtfs.draft.edit.DraftEditService
import eu.transittrack.gtfs.draft.edit.StaleDraftException
import eu.transittrack.gtfs.feed.GtfsFeedRepository
import eu.transittrack.gtfs.revision.GtfsRevision
import eu.transittrack.gtfs.revision.GtfsRevisionStatus

@GraphQlTest
@Import(GraphQlConfiguration::class, DraftEditController::class, DraftMapper::class, GtfsDtoMapper::class)
class DraftEditGraphQlTest(
    @Autowired val tester: GraphQlTester,
) {
    @MockitoBean lateinit var editService: DraftEditService

    @MockitoBean lateinit var draftService: DraftService

    @MockitoBean lateinit var feeds: GtfsFeedRepository

    private fun draft(): GtfsRevision =
        GtfsRevision(
            feedId = 1,
            status = GtfsRevisionStatus.DRAFT,
            sourceUrl = "x",
            version = 4,
        ).apply { id = 5 }

    private fun cannedEdit() =
        DraftEdit(
            revisionId = 5,
            seq = 2,
            op = "SHIFT_TRIP",
            summary = "shifted",
            forward = "{}",
            inverse = "{}",
            appliedAt = Instant.parse("2026-09-01T00:00:00Z"),
        )

    private fun canned() =
        DraftEditService.DraftEditResultData(
            draft = draft(),
            edit = cannedEdit(),
            canUndo = true,
            canRedo = false,
        )

    @Test
    fun `updateStopTime returns a DraftEditResult`() {
        whenever(editService.apply(any(), any(), any(), any())).thenReturn(canned())
        tester
            .document(
                """
                mutation {
                  updateStopTime(input: {
                    draftId: "5", editor: "alice", expectedVersion: 4,
                    tripId: "t1", stopSequence: 2, arrivalSec: 3600, departureSec: 3600
                  }) { draft { id version } edit { op } canUndo canRedo }
                }
                """.trimIndent(),
            ).execute()
            .path("updateStopTime.draft.version")
            .entity(Long::class.java)
            .isEqualTo(4L)
            .path("updateStopTime.edit.op")
            .entity(String::class.java)
            .isEqualTo("SHIFT_TRIP")
            .path("updateStopTime.canUndo")
            .entity(Boolean::class.java)
            .isEqualTo(true)
    }

    @Test
    fun `shiftTrip returns a DraftEditResult`() {
        whenever(editService.apply(any(), any(), any(), any())).thenReturn(canned())
        tester
            .document(
                """mutation { shiftTrip(input: { draftId: "5", editor: "alice", expectedVersion: 4, tripId: "t1", deltaSec: 60 }) { canUndo } }""",
            ).execute()
            .path("shiftTrip.canUndo")
            .entity(Boolean::class.java)
            .isEqualTo(true)
    }

    @Test
    fun `deleteTrip returns a DraftEditResult`() {
        whenever(editService.apply(any(), any(), any(), any())).thenReturn(canned())
        tester
            .document(
                """mutation { deleteTrip(input: { draftId: "5", editor: "alice", expectedVersion: 4, tripId: "t1" }) { draft { id } } }""",
            ).execute()
            .path("deleteTrip.draft.id")
            .entity(String::class.java)
            .isEqualTo("5")
    }

    @Test
    fun `undoDraftEdit returns a DraftEditResult`() {
        whenever(editService.undo(any(), any(), any())).thenReturn(canned())
        tester
            .document("""mutation { undoDraftEdit(id: "5", editor: "alice", expectedVersion: 4) { canUndo canRedo } }""")
            .execute()
            .path("undoDraftEdit.canRedo")
            .entity(Boolean::class.java)
            .isEqualTo(false)
    }

    @Test
    fun `stale draft surfaces STALE_DRAFT error`() {
        whenever(editService.apply(any(), any(), any(), any())).thenThrow(StaleDraftException(7))
        tester
            .document(
                """mutation { shiftTrip(input: { draftId: "5", editor: "alice", expectedVersion: 4, tripId: "t1", deltaSec: 60 }) { canUndo } }""",
            ).execute()
            .errors()
            .satisfy { errors ->
                val ext = errors[0].extensions
                assert(ext["code"] == "STALE_DRAFT") { "code was ${ext["code"]}" }
                assert((ext["currentVersion"] as Number).toLong() == 7L) { "currentVersion was ${ext["currentVersion"]}" }
            }
    }
}
