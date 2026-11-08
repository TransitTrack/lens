import { defineQueryOptions } from '@pinia/colada'
import { Gtfs, type DraftEdit } from '~/api'
import { draftKeys } from '../../keys/drafts'

/** GET /drafts/{id}/edits — the draft's edit history (optionally the latest `limit`). Never kept once unobserved. */
export const draftEditsQuery = defineQueryOptions((p: { id: string | number, limit?: number }) => ({
  key: draftKeys.part(p.id, 'edits', { limit: p.limit }),
  query: async ({ signal }): Promise<DraftEdit[]> =>
    (await Gtfs.draftEdits({ path: { id: String(p.id) }, query: { limit: p.limit }, signal })).data,
  gcTime: 0,
  staleTime: 0,
}))
