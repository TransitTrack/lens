import type { UseMutationOptions } from '@pinia/colada'
import { Drafts, type DiscardDraftData, type DiscardDraftResponse, type ApiError, type Options } from '~/api'
import { draftKeys } from '../../keys/drafts'
import type { QueryCache } from '../types'

export const discardDraftMutation = (queryCache: QueryCache) => ({
  mutation: (vars: Options<DiscardDraftData>) => Drafts.discardDraft({ ...vars, throwOnError: true }).then((r) => r.data),
  onSuccess: (_data: DiscardDraftResponse, _vars: Options<DiscardDraftData>) => {
    queryCache.invalidateQueries({ key: draftKeys.all })
  },
}) satisfies UseMutationOptions<DiscardDraftResponse, Options<DiscardDraftData>, ApiError>
