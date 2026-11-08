import type { UseMutationOptions } from '@pinia/colada'
import { Drafts, type ActivateDraftData, type ActivateDraftResponse, type ApiError, type Options } from '~/api'
import { draftKeys } from '../../keys/drafts'
import { feedKeys } from '../../keys/feeds'
import type { QueryCache } from '../types'

export const activateDraftMutation = (queryCache: QueryCache) => ({
  mutation: (vars: Options<ActivateDraftData>) => Drafts.activateDraft({ ...vars, throwOnError: true }).then((r) => r.data),
  onSuccess: (_data: ActivateDraftResponse, _vars: Options<ActivateDraftData>) => {
    queryCache.invalidateQueries({ key: draftKeys.all })
    queryCache.invalidateQueries({ key: feedKeys.all })
  },
}) satisfies UseMutationOptions<ActivateDraftResponse, Options<ActivateDraftData>, ApiError>
