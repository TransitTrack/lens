import { defineQueryOptions } from '@pinia/colada'
import { Gtfs, optional, type DraftJob } from '~/api'
import { draftKeys } from '../../keys/drafts'

/** GET /draft-jobs/{jobId} — one background job's state, or null when unknown. Polling is set by the caller. */
export const draftJobQuery = defineQueryOptions((p: { jobId: string | number }) => ({
  key: draftKeys.job(p.jobId),
  query: ({ signal }): Promise<DraftJob | null> =>
    optional(Gtfs.draftJob({ path: { jobId: String(p.jobId) }, signal })),
  gcTime: 0,
  staleTime: 0,
}))
