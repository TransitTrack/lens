import type { Rev } from '../keys/feeds'

const present = (v: string | number | null | undefined): v is string | number => v !== null && v !== undefined && v !== ''

/** The `revisionId` request parameter for a scope: the draft's id when reading a draft, else the pinned revision, else omitted (active revision). */
export function revisionParam(s: Rev): string | undefined {
  if (present(s.draftId)) return String(s.draftId)
  return present(s.revisionId) ? String(s.revisionId) : undefined
}

/**
 * Cache settings shared by every revision-scoped query.
 * - draft read: never kept (`gcTime: 0`) and always stale;
 * - explicit revision: immutable, so `staleTime: Infinity`;
 * - active revision: `activeList` reads (routes, stops, …) are fresh for 60 s, everything else inherits the global default.
 */
export function cachePolicy(s: Rev, kind: 'activeList' | 'default' = 'default'): { staleTime?: number, gcTime?: number } {
  if (present(s.draftId)) return { staleTime: 0, gcTime: 0 }
  if (present(s.revisionId)) return { staleTime: Infinity }
  return kind === 'activeList' ? { staleTime: 60_000 } : {}
}
