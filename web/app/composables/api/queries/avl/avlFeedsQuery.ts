import { defineQueryOptions } from '@pinia/colada'
import { AvlFeedCatalog, type AvlFeed } from '~/api'
import { fetchAllPages } from '~/utils/restPagination'
import { avlKeys } from '../../keys/avl'

/** GET /avl-feeds — every AVL feed (all pages). Fresh for 60 s; polling is up to the caller. */
export const avlFeedsQuery = defineQueryOptions({
  key: avlKeys.feeds(),
  query: ({ signal }): Promise<AvlFeed[]> =>
    fetchAllPages((offset, limit) => AvlFeedCatalog.avlFeeds({ query: { offset, limit }, signal })),
  staleTime: 60_000,
})
