import type { UseMutationOptions } from '@pinia/colada'
import { Drafts, type RebuildDraftData, type RebuildDraftResponse, type ApiError, type Options } from '~/api'
import { draftKeys } from '../../keys/drafts'
import type { QueryCache } from '../types'

export const rebuildDraftMutation = (queryCache: QueryCache) => ({
  mutation: (vars: Options<RebuildDraftData>) => Drafts.rebuildDraft({ ...vars, throwOnError: true }).then((r) => r.data),
  onSuccess: (_data: RebuildDraftResponse, vars: Options<RebuildDraftData>) => {
    queryCache.invalidateQueries({ key: draftKeys.draft(vars.path.id) })
  },
}) satisfies UseMutationOptions<RebuildDraftResponse, Options<RebuildDraftData>, ApiError>
