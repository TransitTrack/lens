import { defineQueryOptions } from '@pinia/colada'
import { Optimization, type OptimizationRecommendation, type OptimizationRecommendationStatus } from '~/api'
import { optimizationKeys } from '../../keys/optimization'

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
