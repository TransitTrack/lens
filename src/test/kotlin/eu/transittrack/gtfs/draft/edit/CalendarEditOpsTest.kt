package eu.transittrack.gtfs.draft.edit

import java.time.LocalDate
import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.json.JsonMapper

import eu.transittrack.gtfs.draft.DraftService
import eu.transittrack.gtfs.model.Calendar
import eu.transittrack.gtfs.model.CalendarDateRepository
import eu.transittrack.gtfs.model.CalendarRepository
import eu.transittrack.gtfs.revision.GtfsRevisionRepository
import eu.transittrack.gtfs.support.IngestionTestFactory
import eu.transittrack.support.PostgresPerMethodTest

/** Like [eu.transittrack.gtfs.draft.edit.TimeEditOpsTest]: ingestion + fork commit on their own
 * connections, so this must not run inside a rollback tx. */
@SpringBootTest(classes = [eu.transittrack.Application::class])
@Import(IngestionTestFactory::class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CalendarEditOpsTest(
    @Autowired val svc: DraftEditService,
    @Autowired val drafts: DraftService,
    @Autowired val calendars: CalendarRepository,
    @Autowired val calendarDates: CalendarDateRepository,
    @Autowired val revisions: GtfsRevisionRepository,
    @Autowired val ingestFactory: IngestionTestFactory,
    @Autowired val json: JsonMapper,
) : PostgresPerMethodTest() {
    private fun forkDraft(): Long {
        val (feedCode, _) = ingestFactory.ingest("schedule-sample")
        val draft = drafts.fork(feedCode, null, "T", "alice")
        drafts.claimEditor(draft.id!!, "alice")
        return draft.id!!
    }

    private fun version(draftId: Long): Long = revisions.findById(draftId).orElseThrow().version

    @Test
    fun `SetCalendarOp creates a new service then undo removes it`() {
        val draftId = forkDraft()
        assertThat(calendars.findByServiceId(draftId, "NEW_SVC")).isNull()

        val r1 = svc.apply(draftId, "alice", version(draftId)) { _ ->
            SetCalendarOp(
                "NEW_SVC",
                monday = true, tuesday = true, wednesday = true, thursday = true, friday = true,
                saturday = false, sunday = false,
                startDate = LocalDate.of(2026, 1, 1), endDate = LocalDate.of(2026, 12, 31),
            )
        }
        val created = calendars.findByServiceId(draftId, "NEW_SVC")
        assertThat(created?.monday).isEqualTo(true)
        assertThat(created?.saturday).isEqualTo(false)

        svc.undo(draftId, "alice", r1.draft.version)
        assertThat(calendars.findByServiceId(draftId, "NEW_SVC")).isNull()
    }

    @Test
    fun `SetCalendarOp replaces an existing service then undo restores the old fields`() {
        val draftId = forkDraft()
        val existing = calendars.findByRevisionId(draftId).first()
        val serviceId = existing.serviceId
        val oldMonday = existing.monday
        val oldEnd = existing.endDate

        val r1 = svc.apply(draftId, "alice", version(draftId)) { _ ->
            SetCalendarOp(
                serviceId,
                monday = false, tuesday = false, wednesday = false, thursday = false, friday = false,
                saturday = true, sunday = true,
                startDate = LocalDate.of(2027, 1, 1), endDate = LocalDate.of(2027, 6, 30),
            )
        }
        val updated = calendars.findByServiceId(draftId, serviceId)
        assertThat(updated?.saturday).isEqualTo(true)
        assertThat(updated?.endDate).isEqualTo(LocalDate.of(2027, 6, 30))

        svc.undo(draftId, "alice", r1.draft.version)
        val restored = calendars.findByServiceId(draftId, serviceId)
        assertThat(restored?.monday).isEqualTo(oldMonday)
        assertThat(restored?.endDate).isEqualTo(oldEnd)
    }
}
