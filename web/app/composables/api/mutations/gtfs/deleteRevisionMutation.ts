import type { UseMutationOptions } from '@pinia/colada'
import { Gtfs, type DeleteRevisionData, type DeleteRevisionResponse, type ApiError, type Options } from '~/api'
import { feedKeys } from '../../keys/feeds'
import type { QueryCache } from '../types'

export const deleteRevisionMutation = (queryCache: QueryCache) => ({
  mutation: (vars: Options<DeleteRevisionData>) => Gtfs.deleteRevision({ ...vars, throwOnError: true }).then((r) => r.data),
  onSuccess: (_data: DeleteRevisionResponse, _vars: Options<DeleteRevisionData>) => {
    queryCache.invalidateQueries({ key: feedKeys.all })
  },
}) satisfies UseMutationOptions<DeleteRevisionResponse, Options<DeleteRevisionData>, ApiError>
