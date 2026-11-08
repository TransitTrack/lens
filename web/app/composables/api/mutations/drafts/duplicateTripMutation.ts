import type { UseMutationOptions } from '@pinia/colada'
import { Drafts, type DuplicateTripData, type DuplicateTripResponse, type ApiError, type Options } from '~/api'
import { draftKeys } from '../../keys/drafts'
import type { QueryCache } from '../types'

export const duplicateTripMutation = (queryCache: QueryCache) => ({
  mutation: (vars: Options<DuplicateTripData>) => Drafts.duplicateTrip({ ...vars, throwOnError: true }).then((r) => r.data),
  onSuccess: (_data: DuplicateTripResponse, vars: Options<DuplicateTripData>) => {
    queryCache.invalidateQueries({ key: draftKeys.draft(vars.body.draftId) })
  },
}) satisfies UseMutationOptions<DuplicateTripResponse, Options<DuplicateTripData>, ApiError>
