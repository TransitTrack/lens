import { defineQueryOptions } from '@pinia/colada'
import { Gtfs, type Draft } from '~/api'
import { fetchAllPages } from '~/utils/restPagination'
import { draftKeys } from '../../keys/drafts'

/** GET /drafts — every Draft, optionally of one feed (all pages). Never kept once unobserved. */
export const draftsQuery = defineQueryOptions((p: { feedCode?: string }) => ({
  key: draftKeys.list({ feedCode: p.feedCode }),
  query: ({ signal }): Promise<Draft[]> =>
    fetchAllPages((offset, limit) => Gtfs.drafts({ query: { feedCode: p.feedCode, offset, limit }, signal })),
  gcTime: 0,
  staleTime: 0,
}))
