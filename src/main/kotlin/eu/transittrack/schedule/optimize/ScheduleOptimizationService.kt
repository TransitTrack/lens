package eu.transittrack.schedule.optimize

import java.time.Duration
import java.time.Instant

import org.slf4j.LoggerFactory
import org.springframework.core.task.TaskExecutor
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

import eu.transittrack.gtfs.draft.DraftService
import eu.transittrack.gtfs.draft.edit.DraftEditService
import eu.transittrack.gtfs.draft.edit.EditOp
import eu.transittrack.gtfs.draft.edit.ShiftTripOp
import eu.transittrack.gtfs.draft.edit.UpdateStopTimeOp
import eu.transittrack.gtfs.feed.GtfsFeedRepository
import eu.transittrack.gtfs.revision.GtfsRevision
import eu.transittrack.gtfs.revision.GtfsRevisionRepository
import eu.transittrack.gtfs.revision.GtfsRevisionStatus
import eu.transittrack.observability.TransitTrackMetrics
import eu.transittrack.schedule.optimize.model.OptimizationRecommendationKind
import eu.transittrack.schedule.optimize.model.OptimizationRecommendationRepository
import eu.transittrack.schedule.optimize.model.OptimizationRecommendationRow
import eu.transittrack.schedule.optimize.model.OptimizationRecommendationStatus
import eu.transittrack.schedule.optimize.model.OptimizationRunRepository
import eu.transittrack.schedule.optimize.model.OptimizationRunRow
import eu.transittrack.schedule.optimize.model.OptimizationRunState

/**
 * Thrown by [ScheduleOptimizationService.apply] when two selected recommendations, once expanded
 * into concrete edit targets, assign different values to the same `(tripId, stopSequence)` cell or
 * the same shifted trip. Carries every recommendation id involved in a conflicting group so a
 * caller can report the whole conflict, not just the first offender. Nothing is applied when this
 * is thrown — the caller's `@Transactional` boundary has not persisted anything at that point.
 */
class RecommendationConflictException(
    val conflictingRecommendationIds: Set<Long>,
) : RuntimeException(
        "recommendations $conflictingRecommendationIds propose different values for the same target",
    )

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
    private val draftService: DraftService,
    private val draftEditService: DraftEditService,
    private val json: JsonMapper,
    private val metrics: TransitTrackMetrics = TransitTrackMetrics.forTests(),
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

    fun listRuns(
        feedCode: String,
        limit: Int,
        offset: Int,
    ): List<OptimizationRunRow> {
        val feed = feeds.findByCode(feedCode) ?: throw IllegalArgumentException("no feed '$feedCode'")
        return runs.page(feed.id!!, offset, limit)
    }

    /** Stable `(run_id, status, id)` pagination; `status` null returns every status. */
    fun listRecommendations(
        runId: Long,
        status: OptimizationRecommendationStatus?,
        offset: Int,
        limit: Int,
    ): List<OptimizationRecommendationRow> = recommendations.page(runId, status?.name, offset, limit)

    /**
     * Design section 7 ("Apply workflow"): one atomic orchestration. Locks/reads the successful run
     * and the selected pending recommendations, expands their JSON `proposedValue` into concrete
     * [UpdateStopTimeOp]/[ShiftTripOp] targets, de-duplicates identical edits and rejects divergent
     * assignments to the same target (design section 6.3 — grouped by expanded target, not by
     * `conflictKey`), then forks a new draft from the run's frozen revision, claims [editor] as its
     * lock holder, applies every op in one [DraftEditService.applyBatch] call, and marks the source
     * recommendations `APPLIED`. The whole method is one `@Transactional` boundary so a failure at
     * any step — including a later op in the batch — rolls back the fork, every journal entry, and
     * every version bump; no half-created draft or partial recommendation status change survives.
     */
    @Transactional
    fun apply(
        runId: Long,
        recommendationIds: Set<Long>,
        label: String?,
        editor: String,
    ): GtfsRevision {
        try {
            val updated = applyInternal(runId, recommendationIds, label, editor)
            metrics.optimizationApply(TransitTrackMetrics.ApplyOutcome.SUCCESS)
            return updated
        } catch (e: RecommendationConflictException) {
            metrics.optimizationApply(TransitTrackMetrics.ApplyOutcome.CONFLICT)
            throw e
        } catch (e: IllegalArgumentException) {
            metrics.optimizationApply(TransitTrackMetrics.ApplyOutcome.REJECTED)
            throw e
        } catch (e: IllegalStateException) {
            metrics.optimizationApply(TransitTrackMetrics.ApplyOutcome.REJECTED)
            throw e
        } catch (e: Exception) {
            metrics.optimizationApply(TransitTrackMetrics.ApplyOutcome.FAILURE)
            throw e
        }
    }

    private fun applyInternal(
        runId: Long,
        recommendationIds: Set<Long>,
        label: String?,
        editor: String,
    ): GtfsRevision {
        require(recommendationIds.isNotEmpty()) { "no recommendations selected" }
        val run = runs.findById(runId).orElseThrow { IllegalArgumentException("no run $runId") }
        check(run.state == OptimizationRunState.SUCCEEDED) { "run $runId has not succeeded" }

        // Locks the selected rows for the duration of this transaction so a concurrent apply of an
        // overlapping recommendation-id set serializes on the shared rows instead of racing to
        // fork two drafts from the same PENDING selection (see the repository method's doc).
        val selected = recommendations.findAllByIdForUpdate(recommendationIds).toList()
        require(selected.size == recommendationIds.size) { "one or more recommendation ids do not exist" }
        require(selected.all { it.runId == runId }) { "one or more recommendations do not belong to run $runId" }
        require(selected.all { it.status == OptimizationRecommendationStatus.PENDING }) {
            "only PENDING recommendations can be applied"
        }

        val revision = revisions.findById(run.revisionId).orElse(null)
        checkNotNull(revision) { "run $runId's frozen revision ${run.revisionId} no longer exists" }
        check(revision.feedId == run.feedId) { "run $runId's frozen revision no longer belongs to its feed" }
        check(revision.status == GtfsRevisionStatus.ACTIVE) {
            "run $runId's frozen revision ${run.revisionId} is no longer the active revision " +
                "(status ${revision.status}); a newer import has superseded it"
        }
        val feed = feeds.findById(run.feedId).orElseThrow { IllegalStateException("feed ${run.feedId} no longer exists") }

        val groupedTargets = expandTargets(selected)
        val conflicting =
            groupedTargets.values.filter { group -> group.map { it.valueKey }.distinct().size > 1 }
        if (conflicting.isNotEmpty()) {
            throw RecommendationConflictException(conflicting.flatten().map { it.recommendationId }.toSet())
        }
        val ops = groupedTargets.values.map { it.first().op } // one op per target, dedup applied

        // Every selected recommendation must contribute at least one expanded target — a PENDING row
        // with a missing/empty `proposedValue` would otherwise be silently marked APPLIED below despite
        // no draft edit ever being made on its behalf. Not reachable given the analysis pipeline's own
        // invariants (it never persists an empty `targets` array), but this endpoint must not paper over
        // corrupt data by reporting success.
        val contributingIds = groupedTargets.values
            .flatten()
            .map { it.recommendationId }
            .toSet()
        val noOp = selected.filterNot { it.id in contributingIds }
        check(noOp.isEmpty()) {
            "recommendation(s) ${noOp.map { it.id }} have no proposed targets to apply"
        }

        val draft = draftService.fork(feed.code, run.revisionId, label, editor)
        val lock = draftService.claimEditor(draft.id!!, editor)
        // claimEditor commits its lock via a bulk UPDATE (see GtfsRevisionRepository.tryClaimEditor),
        // which does not refresh Hibernate's already-managed `draft` instance from this same
        // transaction's persistence context. DraftEditService.applyBatch's guard re-reads this same
        // managed row (identity map), so it would otherwise see the pre-claim, unlocked state and
        // reject with LockNotHeldException. Sync the in-memory fields to what was just committed.
        draft.editorClaimBy = lock.editor
        draft.editorClaimExpiresAt = lock.expiresAt
        val updated = draftEditService.applyBatch(draft.id!!, editor, draft.version, ops)

        selected.forEach { it.status = OptimizationRecommendationStatus.APPLIED }
        recommendations.saveAll(selected)

        return updated
    }

    /** One expanded concrete edit target, grouped by [key] (`cell:<tripId>:<seq>` or `shift:<tripId>`). */
    private data class ExpandedTarget(
        val recommendationId: Long,
        val key: String,
        val valueKey: String,
        val op: EditOp,
    )

    /** Design 6.3: group by the *expanded* target, not by a recommendation's own `conflictKey` string. */
    private fun expandTargets(selected: List<OptimizationRecommendationRow>): Map<String, List<ExpandedTarget>> {
        val all = mutableListOf<ExpandedTarget>()
        for (row in selected) {
            val proposed = row.proposedValue ?: continue
            val targets: JsonNode = json.readTree(proposed).path("targets")
            when (row.kind) {
                OptimizationRecommendationKind.STOP_TIME -> {
                    targets.forEach { t -> stopTimeTarget(row, t)?.let(all::add) }
                }

                OptimizationRecommendationKind.TRIP_SHIFT -> {
                    targets.forEach { t -> all += tripShiftTarget(row, t) }
                }
            }
        }
        return all.groupBy { it.key }
    }

    private fun stopTimeTarget(
        row: OptimizationRecommendationRow,
        t: JsonNode,
    ): ExpandedTarget? {
        val tripId = t.path("tripId").asString()
        val seq = t.path("stopSequence").asInt()
        val arrNode = t.path("arrivalSec")
        val depNode = t.path("departureSec")
        // A target whose proposed arrival/departure are both null carries no actual change (see
        // UpdateStopTimeOp's doc: a null arg means "set to NULL", not "leave unchanged") — skip it
        // defensively rather than clobber the cell.
        if (arrNode.isNull && depNode.isNull) return null
        val arr = if (arrNode.isNull) null else arrNode.asInt()
        val dep = if (depNode.isNull) null else depNode.asInt()
        return ExpandedTarget(
            recommendationId = row.id!!,
            key = "cell:$tripId:$seq",
            valueKey = "$arr|$dep",
            op = UpdateStopTimeOp(tripId, seq, arr, dep),
        )
    }

    private fun tripShiftTarget(
        row: OptimizationRecommendationRow,
        t: JsonNode,
    ): ExpandedTarget {
        val tripId = t.path("tripId").asString()
        return ExpandedTarget(
            recommendationId = row.id!!,
            key = "shift:$tripId",
            valueKey = row.deltaSec.toString(),
            op = ShiftTripOp(tripId, row.deltaSec),
        )
    }

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
            metrics.optimizationRunFinished(OptimizationRunState.SUCCEEDED, Duration.between(run.startedAt, run.completedAt))
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
        if (candidates.isNotEmpty()) {
            recommendations.saveAll(candidates)
            candidates.forEach { metrics.optimizationRecommendation(it.kind, it.status) }
        }
    }

    /** Best-effort terminal FAILED with an operator-safe (sanitized) message; the real cause is logged, not stored. */
    private fun markFailed(runId: Long) {
        runCatching {
            runs.findById(runId).ifPresent { run ->
                run.state = OptimizationRunState.FAILED
                run.completedAt = Instant.now()
                run.error = SANITIZED_FAILURE_MESSAGE
                runs.save(run)
                val started = run.startedAt ?: run.completedAt!!
                metrics.optimizationRunFinished(OptimizationRunState.FAILED, Duration.between(started, run.completedAt))
            }
        }.onFailure { log.error("optimization run {} could not be marked FAILED", runId, it) }
    }

    private companion object {
        const val SANITIZED_FAILURE_MESSAGE = "optimization run failed unexpectedly; see server logs"
    }
}
