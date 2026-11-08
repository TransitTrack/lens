import { defineQueryOptions } from '@pinia/colada'
import { Optimization, optional, type OptimizationRun } from '~/api'
import { optimizationKeys } from '../../keys/optimization'

/** GET /optimization-runs/{id} — one run, or null when unknown. */
export const optimizationRunQuery = defineQueryOptions((p: { id: string | number }) => ({
  key: optimizationKeys.run(p.id),
  query: ({ signal }): Promise<OptimizationRun | null> =>
    optional(Optimization.optimizationRun({ path: { id: String(p.id) }, signal })),
}))
