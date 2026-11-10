import { defineQueryOptions } from '@pinia/colada'
import { type Draft, type DraftEdit, type DraftGrid, type DraftJob, Gtfs, optional } from '~/api'
import { fetchAllPages } from '~/utils/restPagination'
import { draftKeys } from '../keys/drafts'

/** GET /drafts/{id}/edits — the draft's edit history (optionally the latest `limit`). Never kept once unobserved. */
export const draftEditsQuery = defineQueryOptions((p: { id: string | number, limit?: number }) => ({
  key: draftKeys.part(p.id, 'edits', { limit: p.limit }),
  query: async ({ signal }): Promise<DraftEdit[]> =>
    (await Gtfs.draftEdits({ path: { id: String(p.id) }, query: { limit: p.limit }, signal })).data,
  gcTime: 0,
  staleTime: 0,
}))

/** GET /drafts/{draftId}/grid — the editable stop-time grid of one route. Never kept once unobserved. */
export const draftGridQuery = defineQueryOptions((p: { draftId: string | number, routeId: string, directionId?: number, serviceId?: string }) => ({
  key: draftKeys.part(p.draftId, 'grid', { routeId: p.routeId, directionId: p.directionId, serviceId: p.serviceId }),
  query: async ({ signal }): Promise<DraftGrid> =>
    (await Gtfs.draftGrid({ path: { draftId: String(p.draftId) }, query: { routeId: p.routeId, directionId: p.directionId, serviceId: p.serviceId }, signal })).data,
  gcTime: 0,
  staleTime: 0,
}))

/** GET /draft-jobs/{jobId} — one background job's state, or null when unknown. Polling is set by the caller. */
export const draftJobQuery = defineQueryOptions((p: { jobId: string | number }) => ({
  key: draftKeys.job(p.jobId),
  query: ({ signal }): Promise<DraftJob | null> =>
    optional(Gtfs.draftJob({ path: { jobId: String(p.jobId) }, signal })),
  gcTime: 0,
  staleTime: 0,
}))

/** GET /drafts/{id} — one Draft, or null when unknown. Never kept once unobserved. */
export const draftQuery = defineQueryOptions((p: { id: string | number }) => ({
  key: draftKeys.part(p.id, 'detail'),
  query: ({ signal }): Promise<Draft | null> =>
    optional(Gtfs.draft({ path: { id: String(p.id) }, signal })),
  gcTime: 0,
  staleTime: 0,
}))

/** GET /drafts — every Draft, optionally of one feed (all pages). Never kept once unobserved. */
export const draftsQuery = defineQueryOptions((p: { feedCode?: string }) => ({
  key: draftKeys.list({ feedCode: p.feedCode }),
  query: ({ signal }): Promise<Draft[]> =>
    fetchAllPages((offset, limit) => Gtfs.drafts({ query: { feedCode: p.feedCode, offset, limit }, signal })),
  gcTime: 0,
  staleTime: 0,
}))
