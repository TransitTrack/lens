import { draftKeys } from '../../keys/drafts'
import type { QueryCache } from '../types'

/**
 * Abort every in-flight read of one draft, keeping the data the page shows. Used when a page
 * leaves a draft that no longer exists (activated / discarded): the mutation's invalidation has
 * started refetches whose 404s would otherwise flash a "draft gone" state before navigation.
 */
export function cancelDraftReads(queryCache: QueryCache, draftId: string | number): void {
  queryCache.cancelQueries({ key: draftKeys.draft(draftId) })
}
