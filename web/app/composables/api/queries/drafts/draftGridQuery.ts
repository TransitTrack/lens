import { defineQueryOptions } from '@pinia/colada'
import { Gtfs, type DraftGrid } from '~/api'
import { draftKeys } from '../../keys/drafts'

/** GET /drafts/{draftId}/grid — the editable stop-time grid of one route. Never kept once unobserved. */
export const draftGridQuery = defineQueryOptions((p: { draftId: string | number, routeId: string, directionId?: number, serviceId?: string }) => ({
  key: draftKeys.part(p.draftId, 'grid', { routeId: p.routeId, directionId: p.directionId, serviceId: p.serviceId }),
  query: async ({ signal }): Promise<DraftGrid> =>
    (await Gtfs.draftGrid({ path: { draftId: String(p.draftId) }, query: { routeId: p.routeId, directionId: p.directionId, serviceId: p.serviceId }, signal })).data,
  gcTime: 0,
  staleTime: 0,
}))
