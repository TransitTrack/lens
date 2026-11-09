import { cleanParams, type KeyParams } from './params'

export type Rev = {
  feedCode: string
  revisionId?: string | number | null
  draftId?: string | number | null
}

const hasId = (v: string | number | null | undefined): v is string | number => v !== null && v !== undefined && v !== ''

export const feedKeys = {
  all: ['feeds'] as const,
  // An object marker, so the list can never collide with feed(code) for an operator-chosen code like 'list';
  // still under `all`, so feed mutations invalidating `all` refresh it.
  list: () => ['feeds', { list: true }] as const,
  feed: (code: string) => ['feeds', code] as const,
  revisions: (code: string, params?: KeyParams) => ['feeds', code, 'revisions', cleanParams(params)] as const,
  rev: (s: Rev) =>
    hasId(s.draftId)
      ? (['drafts', String(s.draftId), 'feeds', s.feedCode] as const)
      : (['feeds', s.feedCode, 'rev', hasId(s.revisionId) ? String(s.revisionId) : 'active'] as const),
  resource: (s: Rev, name: string, params?: KeyParams) => [...feedKeys.rev(s), name, cleanParams(params)] as const,
}
