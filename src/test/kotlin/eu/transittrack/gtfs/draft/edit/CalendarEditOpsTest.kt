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
import eu.transittrack.gtfs.model.CalendarDate
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

    @Test
    fun `SetCalendarExceptionOp adds an exception then undo removes it`() {
        val draftId = forkDraft()
        val serviceId = calendars.findByRevisionId(draftId).first().serviceId
        val date = LocalDate.of(2026, 12, 25)
        assertThat(calendarDates.findByServiceId(draftId, serviceId).none { it.date == date }).isEqualTo(true)

        val r1 = svc.apply(draftId, "alice", version(draftId)) { _ ->
            SetCalendarExceptionOp(serviceId, date, 2)
        }
        val added = calendarDates.findByServiceId(draftId, serviceId).single { it.date == date }
        assertThat(added.exceptionType).isEqualTo(2)

        svc.undo(draftId, "alice", r1.draft.version)
        assertThat(calendarDates.findByServiceId(draftId, serviceId).none { it.date == date }).isEqualTo(true)
    }

    @Test
    fun `SetCalendarExceptionOp with null deletes an existing exception then undo restores it`() {
        val draftId = forkDraft()
        val serviceId = calendars.findByRevisionId(draftId).first().serviceId
        val date = LocalDate.of(2026, 11, 26)
        svc.apply(draftId, "alice", version(draftId)) { _ -> SetCalendarExceptionOp(serviceId, date, 1) }

        val r2 = svc.apply(draftId, "alice", version(draftId)) { _ -> SetCalendarExceptionOp(serviceId, date, null) }
        assertThat(calendarDates.findByServiceId(draftId, serviceId).none { it.date == date }).isEqualTo(true)

        svc.undo(draftId, "alice", r2.draft.version)
        val restored = calendarDates.findByServiceId(draftId, serviceId).single { it.date == date }
        assertThat(restored.exceptionType).isEqualTo(1)
    }

    @Test
    fun `SetCalendarExceptionOp rejects an out-of-range exceptionType`() {
        val draftId = forkDraft()
        val serviceId = calendars.findByRevisionId(draftId).first().serviceId

        try {
            svc.apply(draftId, "alice", version(draftId)) { _ ->
                SetCalendarExceptionOp(serviceId, LocalDate.of(2026, 12, 25), 7)
            }
            error("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message ?: "").isEqualTo("exceptionType must be 1, 2, or null, got 7")
        }
    }

    @Test
    fun `SetCalendarOp rejects startDate after endDate`() {
        val draftId = forkDraft()

        try {
            svc.apply(draftId, "alice", version(draftId)) { _ ->
                SetCalendarOp(
                    "NEW_SVC",
                    monday = true, tuesday = true, wednesday = true, thursday = true, friday = true,
                    saturday = false, sunday = false,
                    startDate = LocalDate.of(2027, 1, 1), endDate = LocalDate.of(2026, 1, 1),
                )
            }
            error("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message ?: "").isEqualTo(
                "startDate (2027-01-01) must not be after endDate (2026-01-01)",
            )
        }
    }

    @Test
    fun `SetCalendarOp undo restores a NULL weekday column as null, not false`() {
        val draftId = forkDraft()
        val serviceId = "NULL_MONDAY_SVC"
        calendars.save(
            Calendar(
                revisionId = draftId,
                serviceId = serviceId,
                monday = null, tuesday = true, wednesday = true, thursday = true,
                friday = true, saturday = false, sunday = false,
                startDate = LocalDate.of(2026, 1, 1), endDate = LocalDate.of(2026, 12, 31),
            ),
        )
        assertThat(calendars.findByServiceId(draftId, serviceId)?.monday).isNull()

        val r1 = svc.apply(draftId, "alice", version(draftId)) { _ ->
            SetCalendarOp(
                serviceId,
                monday = true, tuesday = true, wednesday = true, thursday = true, friday = true,
                saturday = true, sunday = true,
                startDate = LocalDate.of(2026, 1, 1), endDate = LocalDate.of(2026, 12, 31),
            )
        }
        assertThat(calendars.findByServiceId(draftId, serviceId)?.monday).isEqualTo(true)

        svc.undo(draftId, "alice", r1.draft.version)
        assertThat(calendars.findByServiceId(draftId, serviceId)?.monday).isNull()
    }
}
