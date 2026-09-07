package eu.transittrack.schedule.derive

import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import org.junit.jupiter.api.AfterEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

import eu.transittrack.Application
import eu.transittrack.TestcontainersConfiguration
import eu.transittrack.gtfs.revision.GtfsRevisionRepository
import eu.transittrack.gtfs.store.RevisionWriter
import eu.transittrack.gtfs.support.IngestionTestFactory
import eu.transittrack.schedule.model.ScheduleTimeRepository
import eu.transittrack.schedule.model.TripPatternRepository

/**
 * Like [eu.transittrack.gtfs.draft.DraftServiceTest]: the ingestion pieces commit on their own
 * connections, so this must not run inside a rollback transaction — `NOT_SUPPORTED` plus an
 * explicit `@AfterEach` cleanup.
 */
@SpringBootTest(classes = [Application::class])
@Import(TestcontainersConfiguration::class, IngestionTestFactory::class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DerivationServiceTest(
    @Autowired val derivation: DerivationService,
    @Autowired val scheduleWriter: ScheduleWriter,
    @Autowired val patterns: TripPatternRepository,
    @Autowired val scheduleTimes: ScheduleTimeRepository,
    @Autowired val revisions: GtfsRevisionRepository,
    @Autowired val writer: RevisionWriter,
    @Autowired val ingestFactory: IngestionTestFactory,
) {
    private var revToClean: Long? = null

    @AfterEach
    fun clean() {
        revToClean?.let {
            runCatching { scheduleWriter.deleteForRevision(it) }
            runCatching { writer.deleteAllForRevision(it) }
            runCatching { revisions.deleteById(it) }
        }
    }

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
}
