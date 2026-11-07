import { OptimizationApi, ResponseError } from '~~/generated/rest'
import type { OptimizationRecommendationStatus } from '~~/generated/rest'
import type {
  OptimizationRunsQuery, OptimizationRunsQueryVariables, OptimizationRunQuery, OptimizationRunQueryVariables,
  OptimizationRecommendationsQuery, OptimizationRecommendationsQueryVariables,
  StartOptimizationRunMutation, StartOptimizationRunMutationVariables,
  ApplyOptimizationRecommendationsMutation, ApplyOptimizationRecommendationsMutationVariables,
} from '~/types/restViews'
import { useRestQuery, type RestQueryOptions } from './useRestQuery'
import { useRestMutation } from './useRestMutation'

const api = new OptimizationApi()
export function useOptimizationRunsQuery(options: () => RestQueryOptions<OptimizationRunsQueryVariables>) {
  return useRestQuery(options, async (v, signal): Promise<OptimizationRunsQuery> => ({
    optimizationRuns: await api.optimizationRuns({ ...v, limit: v.limit ?? undefined, offset: v.offset ?? undefined }, { signal }) as OptimizationRunsQuery['optimizationRuns'],
  }))
}
export function useOptimizationRunQuery(options: () => RestQueryOptions<OptimizationRunQueryVariables>) {
  return useRestQuery(options, async (v, signal): Promise<OptimizationRunQuery> => {
    try {
      return { optimizationRun: await api.optimizationRun({ id: String(v.id) }, { signal }) as OptimizationRunQuery['optimizationRun'] }
    } catch (error) {
      if (error instanceof ResponseError && error.response.status === 404) return { optimizationRun: null }
      throw error
    }
  })
}
export function useOptimizationRecommendationsQuery(options: () => RestQueryOptions<OptimizationRecommendationsQueryVariables>) {
  return useRestQuery(options, async (v, signal): Promise<OptimizationRecommendationsQuery> => ({
    optimizationRecommendations: await api.optimizationRecommendations({
      ...v, runId: String(v.runId), status: (v.status ?? undefined) as OptimizationRecommendationStatus | undefined,
      limit: v.limit ?? undefined, offset: v.offset ?? undefined,
    }, { signal }) as OptimizationRecommendationsQuery['optimizationRecommendations'],
  }))
}
export function useStartOptimizationRunMutation() {
  return useRestMutation<StartOptimizationRunMutation, StartOptimizationRunMutationVariables>(async (v) => ({
    startOptimizationRun: await api.startOptimizationRun({ optimizationRunInput: v.input } as unknown as Parameters<typeof api.startOptimizationRun>[0]),
  }) as unknown as StartOptimizationRunMutation)
}
export function useApplyOptimizationRecommendationsMutation() {
  return useRestMutation<ApplyOptimizationRecommendationsMutation, ApplyOptimizationRecommendationsMutationVariables>(async (v) => ({
    applyOptimizationRecommendations: await api.applyOptimizationRecommendations({
      runId: String(v.runId), applyOptimizationRecommendationsRequest: {
        recommendationIds: (Array.isArray(v.recommendationIds) ? v.recommendationIds : [v.recommendationIds]).map(String), editor: v.editor, label: v.label ?? undefined,
      },
    }),
  }) as unknown as ApplyOptimizationRecommendationsMutation)
}
