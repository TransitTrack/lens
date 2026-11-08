import type { UseMutationOptions } from '@pinia/colada'
import { Drafts, type ShiftTripData, type ShiftTripResponse, type ApiError, type Options } from '~/api'
import { draftKeys } from '../../keys/drafts'
import type { QueryCache } from '../types'

export const shiftTripMutation = (queryCache: QueryCache) => ({
  mutation: (vars: Options<ShiftTripData>) => Drafts.shiftTrip({ ...vars, throwOnError: true }).then((r) => r.data),
  onSuccess: (_data: ShiftTripResponse, vars: Options<ShiftTripData>) => {
    queryCache.invalidateQueries({ key: draftKeys.draft(vars.body.draftId) })
  },
}) satisfies UseMutationOptions<ShiftTripResponse, Options<ShiftTripData>, ApiError>
