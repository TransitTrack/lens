package eu.transittrack.schedule.optimize

import org.springframework.core.Ordered

import eu.transittrack.schedule.optimize.model.OptimizationRecommendationRow

/**
 * One independently pluggable recommendation-producing strategy (design
 * `docs/superpowers/specs/2026-09-17-optimization-analyzer-architecture-design.md`).
 * [OptimizationAnalysisPipeline] auto-discovers every Spring bean implementing this interface
 * (constructor-injected as `List<OptimizationAnalyzer>`) and runs them in ascending [getOrder]
 * order against one shared [OptimizationAnalysisContext] per run.
 *
 * An analyzer that finds nothing to propose returns an empty list — that is a normal outcome, not
 * an error. An analyzer that throws is caught by the pipeline and logged; it does not fail the run
 * or block other analyzers.
 */
interface OptimizationAnalyzer : Ordered {
    /** Stable, log/metric-facing identifier, e.g. `"stop-time"`. Never persisted or shown to planners. */
    val name: String

    fun analyze(context: OptimizationAnalysisContext): List<OptimizationRecommendationRow>
}
