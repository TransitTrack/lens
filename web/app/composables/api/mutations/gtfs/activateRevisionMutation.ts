import type { UseMutationOptions } from '@pinia/colada'
import { Gtfs, type ActivateRevisionData, type ActivateRevisionResponse, type ApiError, type Options } from '~/api'
import { feedKeys } from '../../keys/feeds'
import type { QueryCache } from '../types'

export const activateRevisionMutation = (queryCache: QueryCache) => ({
  mutation: (vars: Options<ActivateRevisionData>) => Gtfs.activateRevision({ ...vars, throwOnError: true }).then((r) => r.data),
  onSuccess: (_data: ActivateRevisionResponse, _vars: Options<ActivateRevisionData>) => {
    queryCache.invalidateQueries({ key: feedKeys.all })
  },
}) satisfies UseMutationOptions<ActivateRevisionResponse, Options<ActivateRevisionData>, ApiError>
