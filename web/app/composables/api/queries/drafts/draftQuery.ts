import { defineQueryOptions } from '@pinia/colada'
import { Gtfs, optional, type Draft } from '~/api'
import { draftKeys } from '../../keys/drafts'

/** GET /drafts/{id} — one Draft, or null when unknown. Never kept once unobserved. */
export const draftQuery = defineQueryOptions((p: { id: string | number }) => ({
  key: draftKeys.part(p.id, 'detail'),
  query: ({ signal }): Promise<Draft | null> =>
    optional(Gtfs.draft({ path: { id: String(p.id) }, signal })),
  gcTime: 0,
  staleTime: 0,
}))
