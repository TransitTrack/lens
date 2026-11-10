import type { UseMutationOptions } from '@pinia/colada'
import { type ApiError, type ApplyOptimizationRecommendationsData, type ApplyOptimizationRecommendationsResponse, Optimization, type Options, type StartOptimizationRunData, type StartOptimizationRunResponse } from '~/api'
import { draftKeys } from '../keys/drafts'
import { optimizationKeys } from '../keys/optimization'
import type { QueryCache } from './types'

export const applyOptimizationRecommendationsMutation = (queryCache: QueryCache) => ({
  mutation: (vars: Options<ApplyOptimizationRecommendationsData>) => Optimization.applyOptimizationRecommendations({ ...vars, throwOnError: true }).then((r) => r.data),
  onSuccess: (_data: ApplyOptimizationRecommendationsResponse, vars: Options<ApplyOptimizationRecommendationsData>) => {
    queryCache.invalidateQueries({ key: optimizationKeys.run(vars.path.runId) })
    queryCache.invalidateQueries({ key: draftKeys.all })
  },
}) satisfies UseMutationOptions<ApplyOptimizationRecommendationsResponse, Options<ApplyOptimizationRecommendationsData>, ApiError>

export const startOptimizationRunMutation = (queryCache: QueryCache) => ({
  mutation: (vars: Options<StartOptimizationRunData>) => Optimization.startOptimizationRun({ ...vars, throwOnError: true }).then((r) => r.data),
  onSuccess: (_data: StartOptimizationRunResponse, vars: Options<StartOptimizationRunData>) => {
    queryCache.invalidateQueries({ key: optimizationKeys.runs(vars.body.feedCode) })
  },
}) satisfies UseMutationOptions<StartOptimizationRunResponse, Options<StartOptimizationRunData>, ApiError>
