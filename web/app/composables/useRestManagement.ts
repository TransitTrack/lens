import { GTFSApi, ScheduleApi } from '~~/generated/rest'
import type {
  IngestFeedMutation, IngestFeedMutationVariables, ActivateRevisionMutation, ActivateRevisionMutationVariables,
  DeleteRevisionMutation, DeleteRevisionMutationVariables, UpdateFeedMutation, UpdateFeedMutationVariables,
  CompareRevisionsQuery, CompareRevisionsQueryVariables,
} from '~/types/restViews'
import { useRestMutation } from './useRestMutation'
import { useRestQuery, type RestQueryOptions } from './useRestQuery'

const gtfs = new GTFSApi()
const schedule = new ScheduleApi()
export function useCompareRevisionsQuery(options: () => RestQueryOptions<CompareRevisionsQueryVariables>) {
  return useRestQuery(options, async (v, signal): Promise<CompareRevisionsQuery> => ({
    compareRevisions: await schedule.compareRevisions({
      fromRevisionId: String(v.fromRevisionId), toRevisionId: String(v.toRevisionId),
      routeId: v.routeId ?? undefined, directionId: v.directionId ?? undefined,
      serviceId: v.serviceId ?? undefined, offset: v.offset ?? undefined, limit: v.limit ?? undefined,
    }, { signal }) as unknown as CompareRevisionsQuery['compareRevisions'],
  }))
}
export function useIngestFeedMutation() {
  return useRestMutation<IngestFeedMutation, IngestFeedMutationVariables>(async (v) => ({
    ingestFeed: await gtfs.ingestFeed({ feedCode: v.feedCode }),
  }) as unknown as IngestFeedMutation)
}
export function useActivateRevisionMutation() {
  return useRestMutation<ActivateRevisionMutation, ActivateRevisionMutationVariables>(async (v) => ({
    activateRevision: await gtfs.activateRevision({ revisionId: String(v.revisionId) }),
  }) as unknown as ActivateRevisionMutation)
}
export function useDeleteRevisionMutation() {
  return useRestMutation<DeleteRevisionMutation, DeleteRevisionMutationVariables>(async (v) => ({
    deleteRevision: await gtfs.deleteRevision({ id: String(v.revisionId) }),
  }))
}
export function useUpdateFeedMutation() {
  return useRestMutation<UpdateFeedMutation, UpdateFeedMutationVariables>(async (v) => ({
    updateFeed: await gtfs.updateFeed({ code: v.code, updateFeedInput: v.input } as unknown as Parameters<typeof gtfs.updateFeed>[0]),
  }) as unknown as UpdateFeedMutation)
}
