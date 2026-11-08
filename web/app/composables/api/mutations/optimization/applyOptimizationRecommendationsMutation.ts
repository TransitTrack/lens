import type { UseMutationOptions } from '@pinia/colada'
import { Optimization, type ApplyOptimizationRecommendationsData, type ApplyOptimizationRecommendationsResponse, type ApiError, type Options } from '~/api'
import { draftKeys } from '../../keys/drafts'
import { optimizationKeys } from '../../keys/optimization'
import type { QueryCache } from '../types'

export const applyOptimizationRecommendationsMutation = (queryCache: QueryCache) => ({
  mutation: (vars: Options<ApplyOptimizationRecommendationsData>) => Optimization.applyOptimizationRecommendations({ ...vars, throwOnError: true }).then((r) => r.data),
  onSuccess: (_data: ApplyOptimizationRecommendationsResponse, vars: Options<ApplyOptimizationRecommendationsData>) => {
    queryCache.invalidateQueries({ key: optimizationKeys.run(vars.path.runId) })
    queryCache.invalidateQueries({ key: draftKeys.all })
  },
}) satisfies UseMutationOptions<ApplyOptimizationRecommendationsResponse, Options<ApplyOptimizationRecommendationsData>, ApiError>
