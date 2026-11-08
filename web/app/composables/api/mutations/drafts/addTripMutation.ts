import type { UseMutationOptions } from '@pinia/colada'
import { Drafts, type AddTripData, type AddTripResponse, type ApiError, type Options } from '~/api'
import { draftKeys } from '../../keys/drafts'
import type { QueryCache } from '../types'

export const addTripMutation = (queryCache: QueryCache) => ({
  mutation: (vars: Options<AddTripData>) => Drafts.addTrip({ ...vars, throwOnError: true }).then((r) => r.data),
  onSuccess: (_data: AddTripResponse, vars: Options<AddTripData>) => {
    queryCache.invalidateQueries({ key: draftKeys.draft(vars.body.draftId) })
  },
}) satisfies UseMutationOptions<AddTripResponse, Options<AddTripData>, ApiError>
