import type { UseMutationOptions } from '@pinia/colada'
import { Drafts, type DeleteTripData, type DeleteTripResponse, type ApiError, type Options } from '~/api'
import { draftKeys } from '../../keys/drafts'
import type { QueryCache } from '../types'

export const deleteTripMutation = (queryCache: QueryCache) => ({
  mutation: (vars: Options<DeleteTripData>) => Drafts.deleteTrip({ ...vars, throwOnError: true }).then((r) => r.data),
  onSuccess: (_data: DeleteTripResponse, vars: Options<DeleteTripData>) => {
    queryCache.invalidateQueries({ key: draftKeys.draft(vars.body.draftId) })
  },
}) satisfies UseMutationOptions<DeleteTripResponse, Options<DeleteTripData>, ApiError>
