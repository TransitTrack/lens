import type { UseMutationOptions } from '@pinia/colada'
import { Drafts, type UndoDraftEditData, type UndoDraftEditResponse, type ApiError, type Options } from '~/api'
import { draftKeys } from '../../keys/drafts'
import type { QueryCache } from '../types'

export const undoDraftEditMutation = (queryCache: QueryCache) => ({
  mutation: (vars: Options<UndoDraftEditData>) => Drafts.undoDraftEdit({ ...vars, throwOnError: true }).then((r) => r.data),
  onSuccess: (_data: UndoDraftEditResponse, vars: Options<UndoDraftEditData>) => {
    queryCache.invalidateQueries({ key: draftKeys.draft(vars.path.id) })
  },
}) satisfies UseMutationOptions<UndoDraftEditResponse, Options<UndoDraftEditData>, ApiError>
