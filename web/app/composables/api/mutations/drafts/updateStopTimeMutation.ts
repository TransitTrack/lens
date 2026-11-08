import type { UseMutationOptions } from '@pinia/colada'
import { Drafts, type UpdateStopTimeData, type UpdateStopTimeResponse, type ApiError, type Options } from '~/api'
import { draftKeys } from '../../keys/drafts'
import type { QueryCache } from '../types'

export const updateStopTimeMutation = (queryCache: QueryCache) => ({
  mutation: (vars: Options<UpdateStopTimeData>) => Drafts.updateStopTime({ ...vars, throwOnError: true }).then((r) => r.data),
  onSuccess: (_data: UpdateStopTimeResponse, vars: Options<UpdateStopTimeData>) => {
    queryCache.invalidateQueries({ key: draftKeys.draft(vars.body.draftId) })
  },
}) satisfies UseMutationOptions<UpdateStopTimeResponse, Options<UpdateStopTimeData>, ApiError>
