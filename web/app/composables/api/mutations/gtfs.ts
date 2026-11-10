import type { UseMutationOptions } from '@pinia/colada'
import { type ActivateRevisionData, type ActivateRevisionResponse, type ApiError, type DeleteRevisionData, type DeleteRevisionResponse, Gtfs, type IngestFeedData, type IngestFeedResponse, type Options, type UpdateFeedData, type UpdateFeedResponse } from '~/api'
import { feedKeys } from '../keys/feeds'
import type { QueryCache } from './types'

export const activateRevisionMutation = (queryCache: QueryCache) => ({
  mutation: (vars: Options<ActivateRevisionData>) => Gtfs.activateRevision({ ...vars, throwOnError: true }).then((r) => r.data),
  onSuccess: (_data: ActivateRevisionResponse, _vars: Options<ActivateRevisionData>) => {
    queryCache.invalidateQueries({ key: feedKeys.all })
  },
}) satisfies UseMutationOptions<ActivateRevisionResponse, Options<ActivateRevisionData>, ApiError>

export const deleteRevisionMutation = (queryCache: QueryCache) => ({
  mutation: (vars: Options<DeleteRevisionData>) => Gtfs.deleteRevision({ ...vars, throwOnError: true }).then((r) => r.data),
  onSuccess: (_data: DeleteRevisionResponse, _vars: Options<DeleteRevisionData>) => {
    queryCache.invalidateQueries({ key: feedKeys.all })
  },
}) satisfies UseMutationOptions<DeleteRevisionResponse, Options<DeleteRevisionData>, ApiError>

export const ingestFeedMutation = (queryCache: QueryCache) => ({
  mutation: (vars: Options<IngestFeedData>) => Gtfs.ingestFeed({ ...vars, throwOnError: true }).then((r) => r.data),
  onSuccess: (_data: IngestFeedResponse, _vars: Options<IngestFeedData>) => {
    queryCache.invalidateQueries({ key: feedKeys.all })
  },
}) satisfies UseMutationOptions<IngestFeedResponse, Options<IngestFeedData>, ApiError>

export const updateFeedMutation = (queryCache: QueryCache) => ({
  mutation: (vars: Options<UpdateFeedData>) => Gtfs.updateFeed({ ...vars, throwOnError: true }).then((r) => r.data),
  onSuccess: (_data: UpdateFeedResponse, _vars: Options<UpdateFeedData>) => {
    queryCache.invalidateQueries({ key: feedKeys.all })
  },
}) satisfies UseMutationOptions<UpdateFeedResponse, Options<UpdateFeedData>, ApiError>
