import type { UseMutationOptions } from '@pinia/colada'
import { Drafts, type RedoDraftEditData, type RedoDraftEditResponse, type ApiError, type Options } from '~/api'
import { draftKeys } from '../../keys/drafts'
import type { QueryCache } from '../types'

export const redoDraftEditMutation = (queryCache: QueryCache) => ({
  mutation: (vars: Options<RedoDraftEditData>) => Drafts.redoDraftEdit({ ...vars, throwOnError: true }).then((r) => r.data),
  onSuccess: (_data: RedoDraftEditResponse, vars: Options<RedoDraftEditData>) => {
    queryCache.invalidateQueries({ key: draftKeys.draft(vars.path.id) })
  },
}) satisfies UseMutationOptions<RedoDraftEditResponse, Options<RedoDraftEditData>, ApiError>
