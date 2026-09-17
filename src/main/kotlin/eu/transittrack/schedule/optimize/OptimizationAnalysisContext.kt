package eu.transittrack.schedule.optimize

import eu.transittrack.gtfs.model.Trip
import eu.transittrack.predict.model.AvlStopCrossing
import eu.transittrack.predict.model.PredictionAccuracyRepository
import eu.transittrack.schedule.model.ScheduleTime
import eu.transittrack.schedule.model.ScheduleTimeRepository
import eu.transittrack.schedule.model.StopPathRepository
import eu.transittrack.schedule.optimize.model.OptimizationRecommendationRow
import eu.transittrack.schedule.optimize.model.OptimizationRunRow

/**
 * Everything an [OptimizationAnalyzer] may need for one run, built once by
 * [OptimizationAnalysisPipeline] before any analyzer runs.
 *
 * [eligibleTrips] is eager because virtually every analyzer needs the schedule population and it
 * is already a single query. [avlFeedIds]/[crossings] are lazy — computed and cached on first
 * access — specifically because they are AVL-evidence-specific: an analyzer that never consumes
 * AVL crossings (e.g. a future structural analyzer) never pays their query cost. [scheduleFor] and
 * [stopSeqByIndex] memoize per key so multiple analyzers (or multiple partitions within one
 * analyzer) sharing a trip/pattern don't repeat the same repository round trip.
 */
class OptimizationAnalysisContext internal constructor(
    val run: OptimizationRunRow,
    val eligibleTrips: List<Trip>,
    avlFeedIdsSupplier: () -> List<Long>,
    crossingsSupplier: () -> List<AvlStopCrossing>,
    private val scheduleTimes: ScheduleTimeRepository,
    private val stopPaths: StopPathRepository,
    private val predictionAccuracy: PredictionAccuracyRepository,
) {
    val avlFeedIds: List<Long> by lazy(avlFeedIdsSupplier)
    val crossings: List<AvlStopCrossing> by lazy(crossingsSupplier)

    private val scheduleCache = mutableMapOf<Long, List<ScheduleTime>>()

    /** [ScheduleTimeRepository.findByTripOrdered] for this run's frozen revision, memoized per trip row id. */
    fun scheduleFor(tripRowId: Long): List<ScheduleTime> =
        scheduleCache.getOrPut(tripRowId) { scheduleTimes.findByTripOrdered(run.revisionId, tripRowId) }

    private val stopSeqCache = mutableMapOf<Long, Map<Int, Int>>()

    /** `stopPathIndex -> stopSeq` for this run's frozen revision, memoized per trip pattern id. */
    fun stopSeqByIndex(patternId: Long): Map<Int, Int> =
        stopSeqCache.getOrPut(patternId) {
            stopPaths.findByTripPatternOrdered(run.revisionId, patternId).associate { it.stopPathIndex to it.stopSeq }
        }

    /** `prediction_accuracy` is context only, never an input to the timing proposal itself. */
    fun predictionEvidence(
        tripRowIds: List<Long>,
        stopPathIndex: Int,
    ): Map<String, Double?> {
        val rows = predictionAccuracy.findForTargets(avlFeedIds, tripRowIds, stopPathIndex, run.observedFrom, run.observedTo)
        val meanError = if (rows.isEmpty()) null else rows.map { it.errorSec }.average()
        val meanAbsError = if (rows.isEmpty()) null else rows.map { it.absErrorSec }.average()
        return linkedMapOf("predictionMeanErrorSec" to meanError, "predictionMeanAbsErrorSec" to meanAbsError)
    }

    private val produced = mutableListOf<OptimizationRecommendationRow>()

    /** Every recommendation produced by analyzers that already ran this run, in run order. */
    val recommendationsSoFar: List<OptimizationRecommendationRow> get() = produced

    /** Called by [OptimizationAnalysisPipeline] after each analyzer runs; not for analyzers to call. */
    internal fun record(rows: List<OptimizationRecommendationRow>) {
        produced += rows
    }
}
