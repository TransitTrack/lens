package eu.transittrack.gtfs.draft

import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.messageContains
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

import eu.transittrack.gtfs.revision.GtfsRevisionRepository
import eu.transittrack.gtfs.store.RevisionWriter
import eu.transittrack.gtfs.support.IngestionTestFactory
import eu.transittrack.schedule.derive.ScheduleWriter
import eu.transittrack.support.PostgresPerMethodTest

/**
 * Like [DraftServiceTest], the rebuild path commits on its own connections, so this must not run
 * inside a rollback transaction. Uses the real [DraftJobService] bean (real `gtfsIngestExecutor`)
 * and polls the returned job until it leaves `RUNNING`.
 */
@SpringBootTest(classes = [eu.transittrack.Application::class])
@Import(IngestionTestFactory::class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DraftJobServiceTest(
    @Autowired val jobs: DraftJobService,
    @Autowired val drafts: DraftService,
    @Autowired val revisions: GtfsRevisionRepository,
    @Autowired val writer: RevisionWriter,
    @Autowired val scheduleWriter: ScheduleWriter,
    @Autowired val ingestFactory: IngestionTestFactory,
) : PostgresPerMethodTest() {
    private val clean = mutableListOf<Long>()

    @Test
    fun `rebuild job derives then validates`() {
        val (feedCode, base) = ingestFactory.ingest("schedule-sample")
        clean += base
        val draft = drafts.fork(feedCode, null, "J", "alice")
        clean += draft.id!!

        val job = jobs.submitRebuild(draft.id!!)

        var waited = 0
        while (job.state == DraftJob.State.RUNNING && waited < 300) {
            Thread.sleep(100)
            waited++
        }

        assertThat(job.state).isEqualTo(DraftJob.State.SUCCEEDED)
        assertThat(job.phase).isEqualTo(DraftJob.Phase.DONE)
        assertThat(revisions.findById(draft.id!!).get().derivationStale).isEqualTo(false)
        assertThat(revisions.findById(draft.id!!).get().lastValidation).isNotNull()
    }

    @Test
    fun `submitRebuild rejects a draft that is already deriving`() {
        val (feedCode, base) = ingestFactory.ingest("schedule-sample")
        clean += base
        val draft = drafts.fork(feedCode, null, "J", "alice")
        clean += draft.id!!
        revisions.findById(draft.id!!).get().let {
            it.deriving = true
            revisions.save(it)
        }

        val ex = assertThrows<IllegalStateException> { jobs.submitRebuild(draft.id!!) }
        assertThat(ex).messageContains("already rebuilding")
    }
}
