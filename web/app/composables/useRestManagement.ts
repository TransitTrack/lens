import { Gtfs, Schedule } from '~/api'
import type {
  IngestFeedMutation, IngestFeedMutationVariables, ActivateRevisionMutation, ActivateRevisionMutationVariables,
  DeleteRevisionMutation, DeleteRevisionMutationVariables, UpdateFeedMutation, UpdateFeedMutationVariables,
  CompareRevisionsQuery, CompareRevisionsQueryVariables,
} from '~/types/restViews'
import { useRestMutation } from './useRestMutation'
import { useRestQuery, type RestQueryOptions } from './useRestQuery'

export function useCompareRevisionsQuery(options: () => RestQueryOptions<CompareRevisionsQueryVariables>) {
  return useRestQuery('useCompareRevisionsQuery', options, async (v, signal): Promise<CompareRevisionsQuery> => ({
    compareRevisions: (await Schedule.compareRevisions({
      query: {
        fromRevisionId: String(v.fromRevisionId), toRevisionId: String(v.toRevisionId),
        routeId: v.routeId ?? undefined, directionId: v.directionId ?? undefined,
        serviceId: v.serviceId ?? undefined, offset: v.offset ?? undefined, limit: v.limit ?? undefined,
      },
      signal,
    })).data as unknown as CompareRevisionsQuery['compareRevisions'],
  }))
}
export function useIngestFeedMutation() {
  return useRestMutation<IngestFeedMutation, IngestFeedMutationVariables>(async (v) => ({
    ingestFeed: (await Gtfs.ingestFeed({ path: { feedCode: v.feedCode } })).data,
  }) as unknown as IngestFeedMutation)
}
export function useActivateRevisionMutation() {
  return useRestMutation<ActivateRevisionMutation, ActivateRevisionMutationVariables>(async (v) => ({
    activateRevision: (await Gtfs.activateRevision({ path: { revisionId: String(v.revisionId) } })).data,
  }) as unknown as ActivateRevisionMutation)
}
export function useDeleteRevisionMutation() {
  return useRestMutation<DeleteRevisionMutation, DeleteRevisionMutationVariables>(async (v) => ({
    deleteRevision: (await Gtfs.deleteRevision({ path: { id: String(v.revisionId) } })).data,
  }))
}
export function useUpdateFeedMutation() {
  return useRestMutation<UpdateFeedMutation, UpdateFeedMutationVariables>(async (v) => ({
    updateFeed: (await Gtfs.updateFeed({ path: { code: v.code }, body: v.input as never })).data,
  }) as unknown as UpdateFeedMutation)
}
