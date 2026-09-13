package eu.transittrack.gtfs.draft

import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEqualTo
import org.junit.jupiter.api.AfterEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

import eu.transittrack.TestcontainersConfiguration
import eu.transittrack.gtfs.model.StopTimeRepository
import eu.transittrack.gtfs.revision.GtfsRevisionRepository
import eu.transittrack.gtfs.revision.GtfsRevisionStatus
import eu.transittrack.gtfs.store.RevisionWriter
import eu.transittrack.gtfs.support.IngestionTestFactory
import eu.transittrack.schedule.derive.ScheduleWriter

/**
 * `RevisionWriter` and the ingestion pieces commit on their own connections, so — like
 * [eu.transittrack.gtfs.draft.DraftServiceTest] — this must not run inside a rollback
 * transaction. Hence `NOT_SUPPORTED` plus explicit `@AfterEach` cleanup.
 */
@SpringBootTest(classes = [eu.transittrack.Application::class])
@Import(TestcontainersConfiguration::class, IngestionTestFactory::class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DraftLifecycleEndToEndTest(
    @Autowired val drafts: DraftService,
    @Autowired val revisions: GtfsRevisionRepository,
    @Autowired val stopTimes: StopTimeRepository,
    @Autowired val writer: RevisionWriter,
    @Autowired val scheduleWriter: ScheduleWriter,
    @Autowired val ingestFactory: IngestionTestFactory,
) {
    private val cleanup = mutableListOf<Long>()

    @AfterEach
    fun clean() {
        for (id in cleanup) runCatching { scheduleWriter.deleteForRevision(id) }
        for (id in cleanup) runCatching { writer.deleteAllForRevision(id) }
        for (id in cleanup) runCatching { revisions.deleteById(id) }
        cleanup.clear()
    }

    @Test
    fun `fork - edit raw rows - discard`() {
        val (feedCode, activeRev) = ingestFactory.ingest("schedule-sample")
        cleanup += activeRev

        val draft = drafts.fork(feedCode, null, "E2E", "alice")
        cleanup += draft.id!!
        drafts.claimEditor(draft.id!!, "alice")

        // a raw edit: shift the first stop_time of the first trip by +60s
        val anyTrip = stopTimes.findByRevisionId(draft.id!!).first().tripId
        val first = stopTimes.findByTripId(draft.id!!, anyTrip).first()
        first.arrivalTime = (first.arrivalTime ?: 0) + 60
        first.departureTime = (first.departureTime ?: 0) + 60
        stopTimes.save(first)

        // base is untouched
        val baseSt = stopTimes.findByTripId(activeRev, anyTrip).first()
        assertThat(baseSt.arrivalTime).isNotEqualTo(first.arrivalTime)

        drafts.discard(draft.id!!, "alice")
        cleanup.remove(draft.id!!)

        assertThat(revisions.findById(draft.id!!).isPresent).isEqualTo(false)
        assertThat(stopTimes.findByRevisionId(draft.id!!)).isEmpty()

        // active revision still intact
        assertThat(revisions.findById(activeRev).get().status).isEqualTo(GtfsRevisionStatus.ACTIVE)
    }
}
