import type { UseMutationOptions } from '@pinia/colada'
import { Drafts, type BulkShiftTripsData, type BulkShiftTripsResponse, type ApiError, type Options } from '~/api'
import { draftKeys } from '../../keys/drafts'
import type { QueryCache } from '../types'

export const bulkShiftTripsMutation = (queryCache: QueryCache) => ({
  mutation: (vars: Options<BulkShiftTripsData>) => Drafts.bulkShiftTrips({ ...vars, throwOnError: true }).then((r) => r.data),
  onSuccess: (_data: BulkShiftTripsResponse, vars: Options<BulkShiftTripsData>) => {
    queryCache.invalidateQueries({ key: draftKeys.draft(vars.body.draftId) })
  },
}) satisfies UseMutationOptions<BulkShiftTripsResponse, Options<BulkShiftTripsData>, ApiError>
