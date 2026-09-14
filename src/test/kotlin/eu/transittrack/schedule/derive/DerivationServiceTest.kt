package eu.transittrack.schedule.derive

import kotlin.test.Test

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.messageContains
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

import eu.transittrack.Application
import eu.transittrack.gtfs.ingest.IngestionPostProcessor
import eu.transittrack.gtfs.revision.GtfsRevisionRepository
import eu.transittrack.gtfs.revision.RevisionService
import eu.transittrack.gtfs.store.RevisionWriter
import eu.transittrack.gtfs.support.IngestionTestFactory
import eu.transittrack.gtfs.support.derivationService
import eu.transittrack.schedule.model.ScheduleTimeRepository
import eu.transittrack.schedule.model.TripPatternRepository
import eu.transittrack.support.PostgresPerMethodTest

/**
 * Like [eu.transittrack.gtfs.draft.DraftServiceTest]: the ingestion pieces commit on their own
 * connections, so this must not run inside a rollback transaction — `NOT_SUPPORTED`. No manual
 * `DerivationContext.close()` cleanup needed either: `rederive`'s own `finally` block already
 * releases the context on every path (see the second test below), so the only leftover state
 * between tests is the database, which the shared container's per-test truncate now handles.
 */
@SpringBootTest(classes = [Application::class])
@Import(IngestionTestFactory::class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DerivationServiceTest(
    @Autowired val derivation: DerivationService,
    @Autowired val scheduleWriter: ScheduleWriter,
    @Autowired val context: DerivationContext,
    @Autowired val revisionService: RevisionService,
    @Autowired val patterns: TripPatternRepository,
    @Autowired val scheduleTimes: ScheduleTimeRepository,
    @Autowired val revisions: GtfsRevisionRepository,
    @Autowired val writer: RevisionWriter,
    @Autowired val ingestFactory: IngestionTestFactory,
) : PostgresPerMethodTest() {
    private var revToClean: Long? = null

    @Test
    fun `rederive reproduces the same derived model as the ingest pipeline`() {
        val (_, rev) = ingestFactory.ingest("schedule-sample")
        revToClean = rev
        val patternsAfterIngest = patterns.findByRevisionId(rev).size
        val timesAfterIngest = scheduleTimes.findByRevisionId(rev).size
        assertThat(patternsAfterIngest).isGreaterThan(0)

        derivation.rederive(rev)

        assertThat(patterns.findByRevisionId(rev).size).isEqualTo(patternsAfterIngest)
        assertThat(scheduleTimes.findByRevisionId(rev).size).isEqualTo(timesAfterIngest)
        assertThat(revisions.findById(rev).get().derivationStale).isEqualTo(false)
    }

    /**
     * A processor that fails after opening the [DerivationContext] (as `TripPatternProcessor` does)
     * must not leave the context open: the `finally` block in `rederive` owns the close, so a second
     * `rederive` fails with the processor's own error, never "context already open for revision".
     */
    @Test
    fun `a failed rederive releases the derivation context`() {
        val (_, rev) = ingestFactory.ingest("schedule-sample")
        revToClean = rev

        val opensThenThrows =
            object : IngestionPostProcessor {
                override fun postProcess(revisionId: Long): Any {
                    context.open(revisionId)
                    throw IllegalStateException("boom")
                }
            }
        val svc = derivationService(revisionService, revisions, opensThenThrows, context = context)

        assertFailure { svc.rederive(rev) }.messageContains("boom")
        assertFailure { svc.rederive(rev) }.messageContains("boom")
    }
}
