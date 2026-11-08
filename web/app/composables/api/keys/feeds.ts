import { cleanParams, type KeyParams } from './params'

export type Rev = {
  feedCode: string
  revisionId?: string | number | null
  draftId?: string | number | null
}

const hasId = (v: string | number | null | undefined): v is string | number => v !== null && v !== undefined && v !== ''

export const feedKeys = {
  all: ['feeds'] as const,
  list: () => ['feeds', 'list'] as const,
  feed: (code: string) => ['feeds', code] as const,
  revisions: (code: string, params?: KeyParams) => ['feeds', code, 'revisions', cleanParams(params)] as const,
  feedInfo: (code: string) => ['feeds', code, 'feed-info'] as const,
  rev: (s: Rev) =>
    hasId(s.draftId)
      ? (['drafts', String(s.draftId), 'feeds', s.feedCode] as const)
      : (['feeds', s.feedCode, 'rev', hasId(s.revisionId) ? String(s.revisionId) : 'active'] as const),
  resource: (s: Rev, name: string, params?: KeyParams) => [...feedKeys.rev(s), name, cleanParams(params)] as const,
}
