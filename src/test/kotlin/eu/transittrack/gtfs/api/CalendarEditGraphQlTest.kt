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
import eu.transittrack.gtfs.feed.GtfsFeedRepository
import eu.transittrack.gtfs.revision.GtfsRevision
import eu.transittrack.gtfs.revision.GtfsRevisionStatus

@GraphQlTest
@Import(GraphQlConfiguration::class, DraftEditController::class, DraftMapper::class, GtfsDtoMapper::class)
class CalendarEditGraphQlTest(
    @Autowired val tester: GraphQlTester,
) {
    @MockitoBean lateinit var editService: DraftEditService

    @MockitoBean lateinit var draftService: DraftService

    @MockitoBean lateinit var feeds: GtfsFeedRepository

    private fun draft(): GtfsRevision =
        GtfsRevision(feedId = 1, status = GtfsRevisionStatus.DRAFT, sourceUrl = "x", version = 4).apply { id = 5 }

    private fun canned() =
        DraftEditService.DraftEditResultData(
            draft = draft(),
            edit = DraftEdit(
                revisionId = 5, seq = 1, op = "SET_CALENDAR", summary = "set",
                forward = "{}", inverse = "{}", appliedAt = Instant.parse("2026-09-01T00:00:00Z"),
            ),
            canUndo = true,
            canRedo = false,
        )

    @Test
    fun `setCalendar returns a DraftEditResult`() {
        whenever(editService.apply(any(), any(), any(), any())).thenReturn(canned())
        tester
            .document(
                """
                mutation {
                  setCalendar(input: {
                    draftId: "5", editor: "alice", expectedVersion: 4, serviceId: "WD",
                    monday: true, tuesday: true, wednesday: true, thursday: true, friday: true,
                    saturday: false, sunday: false, startDate: "2026-01-01", endDate: "2026-12-31"
                  }) { draft { id version } canUndo }
                }
                """.trimIndent(),
            ).execute()
            .path("setCalendar.draft.version")
            .entity(Long::class.java)
            .isEqualTo(4L)
    }

    @Test
    fun `setCalendarException returns a DraftEditResult`() {
        whenever(editService.apply(any(), any(), any(), any())).thenReturn(canned())
        tester
            .document(
                """
                mutation {
                  setCalendarException(input: {
                    draftId: "5", editor: "alice", expectedVersion: 4, serviceId: "WD",
                    date: "2026-12-25", exceptionType: 2
                  }) { canUndo }
                }
                """.trimIndent(),
            ).execute()
            .path("setCalendarException.canUndo")
            .entity(Boolean::class.java)
            .isEqualTo(true)
    }
}
