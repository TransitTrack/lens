import { defineQueryOptions } from '@pinia/colada'
import { Gtfs, type Feed } from '~/api'
import { fetchAllPages } from '~/utils/restPagination'
import { feedKeys } from '../../keys/feeds'

/** GET /feeds — every configured GTFS feed (all pages). */
export const feedsQuery = defineQueryOptions({
  key: feedKeys.list(),
  query: ({ signal }): Promise<Feed[]> =>
    fetchAllPages((offset, limit) => Gtfs.feeds({ query: { offset, limit }, signal })),
})
