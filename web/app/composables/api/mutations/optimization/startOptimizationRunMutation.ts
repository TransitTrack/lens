import type { UseMutationOptions } from '@pinia/colada'
import { Optimization, type StartOptimizationRunData, type StartOptimizationRunResponse, type ApiError, type Options } from '~/api'
import { optimizationKeys } from '../../keys/optimization'
import type { QueryCache } from '../types'

export const startOptimizationRunMutation = (queryCache: QueryCache) => ({
  mutation: (vars: Options<StartOptimizationRunData>) => Optimization.startOptimizationRun({ ...vars, throwOnError: true }).then((r) => r.data),
  onSuccess: (_data: StartOptimizationRunResponse, vars: Options<StartOptimizationRunData>) => {
    queryCache.invalidateQueries({ key: optimizationKeys.runs(vars.body.feedCode) })
  },
}) satisfies UseMutationOptions<StartOptimizationRunResponse, Options<StartOptimizationRunData>, ApiError>
