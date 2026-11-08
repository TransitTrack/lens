import { defineQueryOptions } from '@pinia/colada'
import { Gtfs, type Revision, type RevisionStatus } from '~/api'
import { fetchAllPages } from '~/utils/restPagination'
import { feedKeys } from '../../keys/feeds'

/** GET /feeds/{feedCode}/revisions — every revision of the feed, optionally filtered by status (all pages). */
export const revisionsQuery = defineQueryOptions((p: { feedCode: string, status?: RevisionStatus }) => ({
  key: feedKeys.revisions(p.feedCode, { status: p.status }),
  query: ({ signal }): Promise<Revision[]> =>
    fetchAllPages((offset, limit) => Gtfs.revisions({ path: { feedCode: p.feedCode }, query: { status: p.status, offset, limit }, signal })),
}))
