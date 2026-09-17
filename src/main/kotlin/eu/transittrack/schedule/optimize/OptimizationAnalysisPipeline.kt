package eu.transittrack.schedule.optimize

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

import eu.transittrack.avl.model.AvlFeedRepository
import eu.transittrack.gtfs.feed.GtfsFeedRepository
import eu.transittrack.gtfs.model.Trip
import eu.transittrack.gtfs.model.TripRepository
import eu.transittrack.observability.TransitTrackMetrics
import eu.transittrack.predict.model.AvlStopCrossing
import eu.transittrack.predict.model.AvlStopCrossingRepository
import eu.transittrack.predict.model.PredictionAccuracyRepository
import eu.transittrack.schedule.model.ScheduleTimeRepository
import eu.transittrack.schedule.model.StopPathRepository
import eu.transittrack.schedule.optimize.model.OptimizationRecommendationRow
import eu.transittrack.schedule.optimize.model.OptimizationRunRow

/**
 * Steps 2-6 of the analysis pipeline (design section 5): resolves the selected schedule population
 * from the run's *frozen* revision (never re-resolved), builds one shared
 * [OptimizationAnalysisContext], then runs every registered [OptimizationAnalyzer] against it in
 * ascending order (design `docs/superpowers/specs/2026-09-17-optimization-analyzer-architecture-design.md`).
 * [ScheduleOptimizationService] owns persistence and the QUEUED/RUNNING/SUCCEEDED/FAILED lifecycle;
 * this class is pure query + orchestration so it stays independently testable.
 */
@Component
class OptimizationAnalysisPipeline(
    private val gtfsFeeds: GtfsFeedRepository,
    private val avlFeeds: AvlFeedRepository,
    private val trips: TripRepository,
    private val scheduleTimes: ScheduleTimeRepository,
    private val stopPaths: StopPathRepository,
    private val crossings: AvlStopCrossingRepository,
    private val predictionAccuracy: PredictionAccuracyRepository,
    analyzers: List<OptimizationAnalyzer>,
    private val metrics: TransitTrackMetrics = TransitTrackMetrics.forTests(),
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val orderedAnalyzers = analyzers.sortedBy { it.order }

    fun analyze(run: OptimizationRunRow): List<OptimizationRecommendationRow> {
        val eligibleTrips =
            trips
                .findEligible(run.revisionId, run.serviceId, run.routeId, run.directionId, run.windowFromSec, run.windowToSec)
                .filter { it.tripPatternId != null && it.id != null }
        log.debug("run {}: {} eligible trips", run.id, eligibleTrips.size)
        if (eligibleTrips.isEmpty()) {
            log.info(
                "run {}: no eligible trips for revision {} (serviceId={}, routeId={}, directionId={}, window={}..{}); " +
                    "0 recommendations",
                run.id, run.revisionId, run.serviceId, run.routeId, run.directionId, run.windowFromSec, run.windowToSec,
            )
            return emptyList()
        }

        lateinit var context: OptimizationAnalysisContext
        context =
            OptimizationAnalysisContext(
                run = run,
                eligibleTrips = eligibleTrips,
                avlFeedIdsSupplier = { avlFeedIdsFor(run.feedId) },
                crossingsSupplier = { loadCrossings(run, eligibleTrips, context.avlFeedIds) },
                scheduleTimes = scheduleTimes,
                stopPaths = stopPaths,
                predictionAccuracy = predictionAccuracy,
            )

        for (analyzer in orderedAnalyzers) {
            val rows =
                try {
                    analyzer.analyze(context)
                } catch (e: Exception) {
                    log.error("run {}: analyzer '{}' failed", run.id, analyzer.name, e)
                    metrics.optimizationAnalyzerFailure(analyzer.name)
                    emptyList()
                }
            log.debug("run {}: analyzer '{}' produced {} recommendations", run.id, analyzer.name, rows.size)
            context.record(rows)
        }

        log.info(
            "run {}: generated {} recommendations from {} analyzer(s)",
            run.id, context.recommendationsSoFar.size, orderedAnalyzers.size,
        )
        return context.recommendationsSoFar
    }

    private fun avlFeedIdsFor(gtfsFeedId: Long): List<Long> {
        val code = gtfsFeeds.findById(gtfsFeedId).orElse(null)?.code ?: return emptyList()
        return avlFeeds.findByGtfsFeedCode(code).mapNotNull { it.id }
    }

    private fun loadCrossings(
        run: OptimizationRunRow,
        eligibleTrips: List<Trip>,
        avlFeedIds: List<Long>,
    ): List<AvlStopCrossing> {
        if (avlFeedIds.isEmpty()) return emptyList()
        val eligibleTripRowIds = eligibleTrips.mapNotNull { it.id }.toSet()
        return crossings
            .findForAnalysis(
                avlFeedIds,
                run.revisionId,
                run.observedFrom,
                run.observedTo,
                run.serviceId,
                run.routeId,
                run.directionId,
                run.windowFromSec,
                run.windowToSec,
            ).filter { it.tripRowId in eligibleTripRowIds }
    }
}
