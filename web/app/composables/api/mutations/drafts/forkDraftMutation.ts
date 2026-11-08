import type { UseMutationOptions } from '@pinia/colada'
import { Drafts, type ForkDraftData, type ForkDraftResponse, type ApiError, type Options } from '~/api'
import { draftKeys } from '../../keys/drafts'
import type { QueryCache } from '../types'

export const forkDraftMutation = (queryCache: QueryCache) => ({
  mutation: (vars: Options<ForkDraftData>) => Drafts.forkDraft({ ...vars, throwOnError: true }).then((r) => r.data),
  onSuccess: (_data: ForkDraftResponse, _vars: Options<ForkDraftData>) => {
    queryCache.invalidateQueries({ key: draftKeys.all })
  },
}) satisfies UseMutationOptions<ForkDraftResponse, Options<ForkDraftData>, ApiError>
