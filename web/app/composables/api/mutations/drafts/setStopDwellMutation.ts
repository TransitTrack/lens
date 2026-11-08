import type { UseMutationOptions } from '@pinia/colada'
import { Drafts, type SetStopDwellData, type SetStopDwellResponse, type ApiError, type Options } from '~/api'
import { draftKeys } from '../../keys/drafts'
import type { QueryCache } from '../types'

export const setStopDwellMutation = (queryCache: QueryCache) => ({
  mutation: (vars: Options<SetStopDwellData>) => Drafts.setStopDwell({ ...vars, throwOnError: true }).then((r) => r.data),
  onSuccess: (_data: SetStopDwellResponse, vars: Options<SetStopDwellData>) => {
    queryCache.invalidateQueries({ key: draftKeys.draft(vars.body.draftId) })
  },
}) satisfies UseMutationOptions<SetStopDwellResponse, Options<SetStopDwellData>, ApiError>
