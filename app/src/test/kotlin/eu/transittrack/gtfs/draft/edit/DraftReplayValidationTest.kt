package eu.transittrack.gtfs.draft.edit

import java.time.LocalDate
import java.util.stream.Stream
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

import eu.transittrack.ProjectJson
import eu.transittrack.gtfs.draft.DraftEditRepository
import eu.transittrack.gtfs.draft.DraftService
import eu.transittrack.gtfs.model.CalendarDateRepository
import eu.transittrack.gtfs.model.CalendarRepository
import eu.transittrack.gtfs.model.FrequencyRepository
import eu.transittrack.gtfs.model.StopTimeRepository
import eu.transittrack.gtfs.model.TripRepository
import eu.transittrack.gtfs.revision.GtfsRevisionRepository
import eu.transittrack.gtfs.support.IngestionTestFactory
import eu.transittrack.support.PostgresPerMethodTest

@SpringBootTest(classes = [eu.transittrack.Application::class])
@Import(IngestionTestFactory::class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DraftReplayValidationTest(
    @Autowired val svc: DraftEditService,
    @Autowired val drafts: DraftService,
    @Autowired val edits: DraftEditRepository,
    @Autowired val revisions: GtfsRevisionRepository,
    @Autowired val trips: TripRepository,
    @Autowired val stopTimes: StopTimeRepository,
    @Autowired val frequencies: FrequencyRepository,
    @Autowired val calendars: CalendarRepository,
    @Autowired val calendarDates: CalendarDateRepository,
    @Autowired val ingestFactory: IngestionTestFactory,
) : PostgresPerMethodTest() {
    @ParameterizedTest(name = "{0}, {1}, redo={2}")
    @MethodSource("invalidPayloads")
    fun `invalid replay leaves rows version and journal unchanged`(
        field: String,
        invalid: String,
        redo: Boolean,
    ) {
        val (feedCode, _) = ingestFactory.ingest("schedule-sample")
        val draft = drafts.fork(feedCode, null, "T", "alice")
        val id = draft.id!!
        drafts.claimEditor(id, "alice")
        val trip = trips.findByRevisionId(id).first { stopTimes.findByTripId(id, it.tripId).size >= 2 }
        val seq = stopTimes.findByTripId(id, trip.tripId).first().stopSequence
        val op = when (field) {
            "arr", "dep" -> UpdateStopTimeOp(trip.tripId, seq, 120, 180)

            "dwell" -> SetStopDwellOp(trip.tripId, seq, 60)

            "monday" -> SetCalendarOp(
                trip.serviceId, true, true, true, true, true, false, false,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
            )

            "pickupType", "shapeDistTraveled" -> RemoveTripStopOp(trip.tripId, seq)

            else -> if (redo) DuplicateTripOp(trip.tripId, "validation_copy", 0) else DeleteTripOp(trip.tripId)
        }
        val applied = svc.apply(id, "alice", draft.version) { op }
        if (redo) svc.undo(id, "alice", applied.draft.version)
        val journal = edits.findByRevision(id).single()
        val original = ProjectJson.parseToJsonElement(if (redo) journal.forward else journal.inverse).jsonObject
        val value = ProjectJson.parseToJsonElement(invalid)
        val changed = when (field) {
            "pickupType", "shapeDistTraveled" -> {
                val rows = original.getValue("rows").jsonArray
                JsonObject(
                    original +
                        (
                            "rows" to JsonArray(
                                rows.mapIndexed { index, row ->
                                    if (index == rows.lastIndex) JsonObject(row.jsonObject + (field to value)) else row
                                },
                            )
                        ),
                )
            }

            "directionId" -> {
                JsonObject(
                    original + ("trip" to JsonObject(original.getValue("trip").jsonObject + (field to value))),
                )
            }

            "headwaySecs" -> {
                JsonObject(
                    original +
                        (
                            "frequencies" to JsonArray(
                                listOf(
                                    JsonObject(
                                        mapOf(
                                            "tripId" to original.getValue("trip").jsonObject.getValue("tripId"),
                                            "startTime" to JsonPrimitive(0),
                                            "headwaySecs" to value,
                                        ),
                                    ),
                                ),
                            )
                        ),
                )
            }

            else -> {
                JsonObject(original + ((if (field == "dwell") "dep" else field) to value))
            }
        }
        if (redo) journal.forward = changed.toString() else journal.inverse = changed.toString()
        edits.save(journal)
        val version = revisions.findById(id)!!.version
        val beforeRows = rows(id)
        val beforeJournal = journalState(id)

        val failure = runCatching {
            if (redo) svc.redo(id, "alice", version) else svc.undo(id, "alice", version)
        }.exceptionOrNull()

        assertEquals(beforeRows, rows(id), "Replay must not persist any row mutations")
        assertEquals(version, revisions.findById(id)!!.version, "Replay must not bump the version")
        assertEquals(beforeJournal, journalState(id), "Replay must not change journal state")
        assertNotNull(failure, "Invalid $field=$invalid must throw")
    }

    private fun rows(id: Long): List<Any?> =
        listOf(
            trips.findByRevisionId(id).sortedBy { it.tripId }.map {
                listOf(it.id, TripLifecycle.snapshot(it, stopTimes.findByTripId(id, it.tripId), frequencies.findByTripId(id, it.tripId)))
            },
            stopTimes.findByRevisionId(id).map { it.id },
            calendars.findByRevisionId(id).map {
                listOf(
                    it.id, it.serviceId, it.monday, it.tuesday, it.wednesday, it.thursday,
                    it.friday, it.saturday, it.sunday, it.startDate, it.endDate,
                )
            },
            calendarDates.findByRevisionId(id).map { listOf(it.id, it.serviceId, it.date, it.exceptionType) },
        )

    private fun journalState(id: Long) =
        edits.findByRevision(id).map { listOf(it.id, it.seq, it.op, it.forward, it.inverse, it.undone, it.appliedAt) }

    companion object {
        @JvmStatic
        fun invalidPayloads(): Stream<Arguments> =
            listOf(
                "arr" to "\"bad\"",
                "dep" to "2147483648",
                "dwell" to "\"bad\"",
                "pickupType" to "\"bad\"",
                "pickupType" to "2147483648",
                "shapeDistTraveled" to "\"bad\"",
                "shapeDistTraveled" to "1e400",
                "directionId" to "\"bad\"",
                "directionId" to "2147483648",
                "headwaySecs" to "\"bad\"",
                "headwaySecs" to "2147483648",
                "monday" to "\"bad\"",
            ).flatMap { (field, invalid) -> listOf(false, true).map { Arguments.of(field, invalid, it) } }.stream()
    }
}
