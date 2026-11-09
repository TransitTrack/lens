import { draftKeys } from '../../keys/drafts'
import type { QueryCache } from '../types'

/**
 * Refresh every read of one draft once (the end of an undo burst, see `undoDraftEditStepMutation`).
 * A failed or aborted refetch is left to the queries' own error state.
 */
export function invalidateDraft(queryCache: QueryCache, draftId: string | number): Promise<void> {
  return queryCache.invalidateQueries({ key: draftKeys.draft(draftId) }).then(() => undefined, () => undefined)
}

/**
 * Abort every in-flight read of one draft, keeping the data the page shows. Used when a page
 * leaves a draft that no longer exists (activated / discarded): the mutation's invalidation has
 * started refetches whose 404s would otherwise flash a "draft gone" state before navigation.
 */
export function cancelDraftReads(queryCache: QueryCache, draftId: string | number): void {
  queryCache.cancelQueries({ key: draftKeys.draft(draftId) })
}
