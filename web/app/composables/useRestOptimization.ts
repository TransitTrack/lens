import { Optimization, optional } from '~/api'
import type { OptimizationRecommendationStatus, OptimizationRunInput } from '~/api'
import type {
  OptimizationRunsQuery, OptimizationRunsQueryVariables, OptimizationRunQuery, OptimizationRunQueryVariables,
  OptimizationRecommendationsQuery, OptimizationRecommendationsQueryVariables,
  StartOptimizationRunMutation, StartOptimizationRunMutationVariables,
  ApplyOptimizationRecommendationsMutation, ApplyOptimizationRecommendationsMutationVariables,
} from '~/types/restViews'
import { useRestQuery, type RestQueryOptions } from './useRestQuery'
import { useRestMutation } from './useRestMutation'

export function useOptimizationRunsQuery(options: () => RestQueryOptions<OptimizationRunsQueryVariables>) {
  return useRestQuery('useOptimizationRunsQuery', options, async (v, signal): Promise<OptimizationRunsQuery> => ({
    optimizationRuns: (await Optimization.optimizationRuns({
      path: { feedCode: v.feedCode }, query: { limit: v.limit ?? undefined, offset: v.offset ?? undefined }, signal,
    })).data as OptimizationRunsQuery['optimizationRuns'],
  }))
}
export function useOptimizationRunQuery(options: () => RestQueryOptions<OptimizationRunQueryVariables>) {
  return useRestQuery('useOptimizationRunQuery', options, async (v, signal): Promise<OptimizationRunQuery> => ({
    optimizationRun: await optional(Optimization.optimizationRun({ path: { id: String(v.id) }, signal })) as OptimizationRunQuery['optimizationRun'],
  }))
}
export function useOptimizationRecommendationsQuery(options: () => RestQueryOptions<OptimizationRecommendationsQueryVariables>) {
  return useRestQuery('useOptimizationRecommendationsQuery', options, async (v, signal): Promise<OptimizationRecommendationsQuery> => ({
    optimizationRecommendations: (await Optimization.optimizationRecommendations({
      path: { runId: String(v.runId) },
      query: { status: (v.status ?? undefined) as OptimizationRecommendationStatus | undefined, limit: v.limit ?? undefined, offset: v.offset ?? undefined },
      signal,
    })).data as OptimizationRecommendationsQuery['optimizationRecommendations'],
  }))
}
export function useStartOptimizationRunMutation() {
  return useRestMutation<StartOptimizationRunMutation, StartOptimizationRunMutationVariables>(async (v) => ({
    startOptimizationRun: (await Optimization.startOptimizationRun({ body: v.input as unknown as OptimizationRunInput })).data,
  }) as unknown as StartOptimizationRunMutation)
}
export function useApplyOptimizationRecommendationsMutation() {
  return useRestMutation<ApplyOptimizationRecommendationsMutation, ApplyOptimizationRecommendationsMutationVariables>(async (v) => ({
    applyOptimizationRecommendations: (await Optimization.applyOptimizationRecommendations({
      path: { runId: String(v.runId) },
      body: {
        recommendationIds: (Array.isArray(v.recommendationIds) ? v.recommendationIds : [v.recommendationIds]).map(String), editor: v.editor, label: v.label ?? undefined,
      },
    })).data,
  }) as unknown as ApplyOptimizationRecommendationsMutation)
}
