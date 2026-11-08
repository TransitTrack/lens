import { defineQueryOptions } from '@pinia/colada'
import { Optimization, type OptimizationRun } from '~/api'
import { optimizationKeys } from '../../keys/optimization'

/**
 * GET /feeds/{feedCode}/optimization-runs — ONE page of runs (callers pass limit / offset; the old
 * composable never read every page). Polling is up to the caller.
 */
export const optimizationRunsQuery = defineQueryOptions((p: { feedCode: string, limit?: number, offset?: number }) => ({
  key: [...optimizationKeys.runs(p.feedCode), { limit: p.limit, offset: p.offset }] as const,
  query: async ({ signal }): Promise<OptimizationRun[]> =>
    (await Optimization.optimizationRuns({ path: { feedCode: p.feedCode }, query: { limit: p.limit, offset: p.offset }, signal })).data,
}))
