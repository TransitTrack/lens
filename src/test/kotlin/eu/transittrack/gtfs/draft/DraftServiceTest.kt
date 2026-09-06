package eu.transittrack.gtfs.draft

import kotlin.test.Test

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import org.junit.jupiter.api.AfterEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

import eu.transittrack.TestcontainersConfiguration
import eu.transittrack.gtfs.feed.GtfsFeedRepository
import eu.transittrack.gtfs.model.StopRepository
import eu.transittrack.gtfs.revision.GtfsRevisionRepository
import eu.transittrack.gtfs.revision.GtfsRevisionStatus
import eu.transittrack.gtfs.store.RevisionWriter
import eu.transittrack.gtfs.support.IngestionTestFactory

/**
 * `RevisionWriter` and the ingestion pieces commit on their own connections, so — like
 * [eu.transittrack.gtfs.revision.RevisionServiceTest] — this must not run inside a rollback
 * transaction. Hence `NOT_SUPPORTED` plus explicit `@AfterEach` cleanup.
 */
@SpringBootTest(classes = [eu.transittrack.Application::class])
@Import(TestcontainersConfiguration::class, IngestionTestFactory::class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DraftServiceTest(
    @Autowired val drafts: DraftService,
    @Autowired val revisions: GtfsRevisionRepository,
    @Autowired val feeds: GtfsFeedRepository,
    @Autowired val stops: StopRepository,
    @Autowired val writer: RevisionWriter,
    @Autowired val ingestFactory: IngestionTestFactory,
) {
    private val cleanupRevs = mutableListOf<Long>()

    @AfterEach
    fun cleanup() {
        for (id in cleanupRevs) runCatching { writer.deleteAllForRevision(id) }
        for (id in cleanupRevs) runCatching { revisions.deleteById(id) }
        runCatching { feeds.findByCode("schedule-sample")?.id?.let { feeds.deleteById(it) } }
        cleanupRevs.clear()
    }

    @Test
    fun `fork copies the active revision and isolates it`() {
        val (feedCode, activeRev) = ingestFactory.ingest("schedule-sample")
        cleanupRevs += activeRev
        val baseStops = stops.findByRevisionId(activeRev).size

        val draft = drafts.fork(feedCode, null, "Proposal A", "alice")
        cleanupRevs += draft.id!!

        assertThat(draft.kind).isEqualTo(DraftKind.DRAFT)
        assertThat(draft.status).isEqualTo(GtfsRevisionStatus.DRAFT)
        assertThat(draft.baseRevisionId).isEqualTo(activeRev)
        assertThat(draft.derivationStale).isEqualTo(true)
        assertThat(stops.findByRevisionId(draft.id!!)).hasSize(baseStops)
        assertThat(draft.rowCounts["stops"]).isEqualTo(baseStops.toLong())
    }

    @Test
    fun `fork with unknown feed fails`() {
        assertFailure { drafts.fork("nope", null, null, null) }.isInstanceOf<IllegalArgumentException>()
    }
}
