import type { UseMutationOptions } from '@pinia/colada'
import { Gtfs, type UpdateFeedData, type UpdateFeedResponse, type ApiError, type Options } from '~/api'
import { feedKeys } from '../../keys/feeds'
import type { QueryCache } from '../types'

export const updateFeedMutation = (queryCache: QueryCache) => ({
  mutation: (vars: Options<UpdateFeedData>) => Gtfs.updateFeed({ ...vars, throwOnError: true }).then((r) => r.data),
  onSuccess: (_data: UpdateFeedResponse, _vars: Options<UpdateFeedData>) => {
    queryCache.invalidateQueries({ key: feedKeys.all })
  },
}) satisfies UseMutationOptions<UpdateFeedResponse, Options<UpdateFeedData>, ApiError>
