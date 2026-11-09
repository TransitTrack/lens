import type { UseMutationOptions } from '@pinia/colada'
import { Drafts, type UndoDraftEditData, type UndoDraftEditResponse, type ApiError, type Options } from '~/api'
import { draftKeys } from '../../keys/drafts'
import type { QueryCache } from '../types'

/**
 * One step of an "undo to here" burst: POST /drafts/{id}/undo without invalidating anything,
 * so a burst of N steps doesn't refetch the draft's reads N times. The caller ends the burst
 * with {@link invalidateDraft} once.
 */
export const undoDraftEditStepMutation = () => ({
  mutation: (vars: Options<UndoDraftEditData>) => Drafts.undoDraftEdit({ ...vars, throwOnError: true }).then((r) => r.data),
}) satisfies UseMutationOptions<UndoDraftEditResponse, Options<UndoDraftEditData>, ApiError>

/** Refresh every read of one draft once (the end of an undo burst). A failed or aborted refetch is left to the queries' own error state. */
export function invalidateDraft(queryCache: QueryCache, draftId: string | number): Promise<void> {
  return queryCache.invalidateQueries({ key: draftKeys.draft(draftId) }).then(() => undefined, () => undefined)
}
