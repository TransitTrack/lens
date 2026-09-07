package eu.transittrack.gtfs.draft

import java.util.Collections
import java.util.UUID

import org.slf4j.LoggerFactory
import org.springframework.core.task.TaskExecutor
import org.springframework.stereotype.Service

import eu.transittrack.gtfs.export.GtfsRevisionValidator
import eu.transittrack.gtfs.revision.GtfsRevisionRepository
import eu.transittrack.schedule.derive.DerivationService

class DraftJob(
    val id: String,
    @Volatile var state: State,
    @Volatile var phase: Phase,
    @Volatile var error: String? = null,
) {
    enum class State { RUNNING, SUCCEEDED, FAILED }

    enum class Phase { DERIVING, VALIDATING, DONE }
}

/**
 * In-memory registry that runs a draft "Rebuild" — [DerivationService.rederive] followed by
 * [GtfsRevisionValidator.validate] — on the shared `gtfsIngestExecutor` and exposes each run as a
 * pollable [DraftJob].
 *
 * Jobs are held in an insertion-ordered, bounded map: once past [MAX_JOBS] the eldest entry is
 * evicted on insert (`removeEldestEntry`). This can drop a still-`RUNNING` job only after 200 newer
 * submissions, which is well beyond any realistic backlog.
 */
@Service
class DraftJobService(
    private val derivation: DerivationService,
    private val validator: GtfsRevisionValidator,
    private val gtfsIngestExecutor: TaskExecutor,
    private val revisions: GtfsRevisionRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val jobs: MutableMap<String, DraftJob> =
        Collections.synchronizedMap(
            object : LinkedHashMap<String, DraftJob>(64, 0.75f, false) {
                override fun removeEldestEntry(eldest: Map.Entry<String, DraftJob>): Boolean = size > MAX_JOBS
            },
        )

    fun submitRebuild(draftId: Long): DraftJob {
        revisions.findById(draftId).ifPresent {
            check(!it.deriving) { "draft $draftId is already rebuilding" }
        }
        val job = DraftJob(UUID.randomUUID().toString(), DraftJob.State.RUNNING, DraftJob.Phase.DERIVING)
        jobs[job.id] = job
        revisions.findById(draftId).ifPresent {
            it.deriving = true
            revisions.save(it)
        }
        gtfsIngestExecutor.execute {
            try {
                derivation.rederive(draftId)
                job.phase = DraftJob.Phase.VALIDATING
                validator.validate(draftId)
                job.phase = DraftJob.Phase.DONE
                job.state = DraftJob.State.SUCCEEDED
            } catch (e: Exception) {
                log.warn("rebuild job {} for draft {} failed", job.id, draftId, e)
                job.state = DraftJob.State.FAILED
                job.error = e.message ?: e.javaClass.simpleName
                revisions.findById(draftId).ifPresent {
                    it.deriving = false
                    revisions.save(it)
                }
            }
        }
        return job
    }

    fun get(jobId: String): DraftJob? = jobs[jobId]

    private companion object {
        const val MAX_JOBS = 200
    }
}
