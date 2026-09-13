package eu.transittrack.schedule.optimize

import java.time.Instant

import org.slf4j.LoggerFactory
import org.springframework.core.task.TaskExecutor
import org.springframework.stereotype.Service

import eu.transittrack.gtfs.feed.GtfsFeedRepository
import eu.transittrack.gtfs.revision.GtfsRevisionRepository
import eu.transittrack.gtfs.revision.GtfsRevisionStatus
import eu.transittrack.schedule.optimize.model.OptimizationRecommendationRepository
import eu.transittrack.schedule.optimize.model.OptimizationRecommendationRow
import eu.transittrack.schedule.optimize.model.OptimizationRecommendationStatus
import eu.transittrack.schedule.optimize.model.OptimizationRunRepository
import eu.transittrack.schedule.optimize.model.OptimizationRunRow
import eu.transittrack.schedule.optimize.model.OptimizationRunState

/**
 * Normalized analysis request. Validated eagerly in [init] — before any repository access — so a
 * malformed request never touches the database: `observedFrom`/`observedTo` must be an increasing
 * pair, `minimumSamples` must be positive, and the optional departure-time window, when given, must
 * be a paired, increasing (`windowFromSec < windowToSec`) GTFS-seconds range.
 */
data class OptimizationRunRequest(
    val feedCode: String,
    val serviceId: String? = null,
    val routeId: String? = null,
    val directionId: Int? = null,
    val windowFromSec: Int? = null,
    val windowToSec: Int? = null,
    val observedFrom: Instant,
    val observedTo: Instant,
    val minimumSamples: Int,
) {
    init {
        require(observedFrom < observedTo) { "observedFrom must be before observedTo" }
        require(minimumSamples > 0) { "minimumSamples must be positive" }
        if (windowFromSec != null || windowToSec != null) {
            requireNotNull(windowFromSec) { "windowFromSec and windowToSec must both be set" }
            requireNotNull(windowToSec) { "windowFromSec and windowToSec must both be set" }
            require(windowFromSec < windowToSec) { "windowFromSec must be before windowToSec" }
        }
    }
}

/**
 * Submits and tracks optimization runs. `submit` resolves the feed's ACTIVE revision and persists a
 * `QUEUED` row synchronously, then dispatches the actual analysis onto the dedicated one-core
 * `scheduleOptimizationExecutor` (registered in `eu.transittrack.config.Configurations`). The run's
 * `revisionId` is frozen at submission and never re-read from the feed afterward, so a later
 * activation cannot change which revision an in-flight run analyzes.
 *
 * The analysis body itself ([runAnalysis]) delegates the actual query/partition/trim/generate work
 * (design section 5 steps 2-6) to [OptimizationAnalysisPipeline], keeping this class focused on the
 * QUEUED/RUNNING/SUCCEEDED/FAILED lifecycle.
 */
@Service
class ScheduleOptimizationService(
    private val feeds: GtfsFeedRepository,
    private val revisions: GtfsRevisionRepository,
    private val runs: OptimizationRunRepository,
    private val recommendations: OptimizationRecommendationRepository,
    private val pipeline: OptimizationAnalysisPipeline,
    private val scheduleOptimizationExecutor: TaskExecutor,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun submit(request: OptimizationRunRequest): OptimizationRunRow {
        val feed = feeds.findByCode(request.feedCode) ?: throw IllegalArgumentException("no feed '${request.feedCode}'")
        val revision = revisions.findByFeedAndStatus(feed.id!!, GtfsRevisionStatus.ACTIVE)
            ?: throw IllegalArgumentException("feed '${request.feedCode}' has no ACTIVE revision")

        val run =
            runs.save(
                OptimizationRunRow(
                    feedId = feed.id!!,
                    revisionId = revision.id!!,
                    serviceId = request.serviceId,
                    routeId = request.routeId,
                    directionId = request.directionId,
                    windowFromSec = request.windowFromSec,
                    windowToSec = request.windowToSec,
                    observedFrom = request.observedFrom,
                    observedTo = request.observedTo,
                    minimumSamples = request.minimumSamples,
                ),
            )

        dispatch(run.id!!)
        return run
    }

    fun get(runId: Long): OptimizationRunRow? = runs.findById(runId).orElse(null)

    /** Stable `(run_id, status, id)` pagination; `status` null returns every status. */
    fun listRecommendations(
        runId: Long,
        status: OptimizationRecommendationStatus?,
        offset: Int,
        limit: Int,
    ): List<OptimizationRecommendationRow> = recommendations.page(runId, status?.name, offset, limit)

    private fun dispatch(runId: Long) {
        try {
            scheduleOptimizationExecutor.execute { runOne(runId) }
        } catch (e: RuntimeException) {
            // execute() itself threw (RejectedExecutionException during shutdown/overload) — the
            // task body never ran, so the run must still be terminated rather than stuck QUEUED.
            log.warn("optimization run {} could not be dispatched", runId, e)
            markFailed(runId)
        }
    }

    private fun runOne(runId: Long) {
        try {
            val run = runs.findById(runId).orElse(null) ?: return
            run.state = OptimizationRunState.RUNNING
            run.startedAt = Instant.now()
            runs.save(run)

            runAnalysis(run)

            run.state = OptimizationRunState.SUCCEEDED
            run.completedAt = Instant.now()
            runs.save(run)
        } catch (t: Exception) {
            log.error("optimization run {} failed", runId, t)
            markFailed(runId)
        }
    }

    /**
     * Runs the real pipeline and persists only the resulting candidates. An empty eligible
     * population or insufficient evidence yields an empty list — a successful run with zero
     * recommendations, per the design's error-handling rules, not an error.
     */
    private fun runAnalysis(run: OptimizationRunRow) {
        val candidates = pipeline.analyze(run)
        if (candidates.isNotEmpty()) recommendations.saveAll(candidates)
    }

    /** Best-effort terminal FAILED with an operator-safe (sanitized) message; the real cause is logged, not stored. */
    private fun markFailed(runId: Long) {
        runCatching {
            runs.findById(runId).ifPresent { run ->
                run.state = OptimizationRunState.FAILED
                run.completedAt = Instant.now()
                run.error = SANITIZED_FAILURE_MESSAGE
                runs.save(run)
            }
        }.onFailure { log.error("optimization run {} could not be marked FAILED", runId, it) }
    }

    private companion object {
        const val SANITIZED_FAILURE_MESSAGE = "optimization run failed unexpectedly; see server logs"
    }
}
