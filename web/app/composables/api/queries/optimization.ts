import { defineQueryOptions } from '@pinia/colada'
import { Optimization, type OptimizationRecommendation, type OptimizationRecommendationStatus, type OptimizationRun, optional } from '~/api'
import { optimizationKeys } from '../keys/optimization'

/** GET /optimization-runs/{runId}/recommendations — ONE page of a run's recommendations (callers pass limit / offset). */
export const optimizationRecommendationsQuery = defineQueryOptions((p: {
  runId: string | number, status?: OptimizationRecommendationStatus, offset?: number, limit?: number
}) => ({
  key: optimizationKeys.recommendations(p.runId, { status: p.status, offset: p.offset, limit: p.limit }),
  query: async ({ signal }): Promise<OptimizationRecommendation[]> =>
    (await Optimization.optimizationRecommendations({
      path: { runId: String(p.runId) },
      query: { status: p.status, offset: p.offset, limit: p.limit },
      signal,
    })).data,
}))

/** GET /optimization-runs/{id} — one run, or null when unknown. */
export const optimizationRunQuery = defineQueryOptions((p: { id: string | number }) => ({
  key: optimizationKeys.run(p.id),
  query: ({ signal }): Promise<OptimizationRun | null> =>
    optional(Optimization.optimizationRun({ path: { id: String(p.id) }, signal })),
}))

/**
 * GET /feeds/{feedCode}/optimization-runs — ONE page of runs (callers pass limit / offset; the old
 * composable never read every page). Polling is up to the caller.
 */
export const optimizationRunsQuery = defineQueryOptions((p: { feedCode: string, limit?: number, offset?: number }) => ({
  key: [...optimizationKeys.runs(p.feedCode), { limit: p.limit, offset: p.offset }] as const,
  query: async ({ signal }): Promise<OptimizationRun[]> =>
    (await Optimization.optimizationRuns({ path: { feedCode: p.feedCode }, query: { limit: p.limit, offset: p.offset }, signal })).data,
}))
