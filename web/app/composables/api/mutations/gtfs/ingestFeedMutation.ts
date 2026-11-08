import type { UseMutationOptions } from '@pinia/colada'
import { Gtfs, type IngestFeedData, type IngestFeedResponse, type ApiError, type Options } from '~/api'
import { feedKeys } from '../../keys/feeds'
import type { QueryCache } from '../types'

export const ingestFeedMutation = (queryCache: QueryCache) => ({
  mutation: (vars: Options<IngestFeedData>) => Gtfs.ingestFeed({ ...vars, throwOnError: true }).then((r) => r.data),
  onSuccess: (_data: IngestFeedResponse, _vars: Options<IngestFeedData>) => {
    queryCache.invalidateQueries({ key: feedKeys.all })
  },
}) satisfies UseMutationOptions<IngestFeedResponse, Options<IngestFeedData>, ApiError>
