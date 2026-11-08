import { createSharedComposable } from '@vueuse/core'
import { Gtfs } from '~/api'
import type { AvlFeed, Feed } from '~/api'
import { fetchAllPages } from '~/utils/restPagination'
import { useAvlFeedsQuery } from './useRestGtfs'
import { useRestQuery } from './useRestQuery'

/**
 * Feed lists for feed selection. Both reads go through the shared query cache, so the AVL list is
 * the same request (and the same 60 s timer) as every other `useAvlFeedsQuery` caller rather than a
 * second poll of `/avl-feeds`. A failed poll keeps the last successful list.
 */
export const useRestFeedCatalog = createSharedComposable(() => {
  const gtfs = useRestQuery(
    'useGtfsFeedCatalog',
    () => ({ variables: {}, pollInterval: 120_000 }),
    (_variables, signal) =>
      fetchAllPages((offset, limit) => Gtfs.feeds({ query: { offset, limit }, signal })),
  )
  const avl = useAvlFeedsQuery(() => ({ pollInterval: 120_000 }))

  return {
    gtfsFeeds: computed<Feed[]>(() => gtfs.result.value ?? []),
    avlFeeds: computed<AvlFeed[]>(() => (avl.result.value?.avlFeeds ?? []) as unknown as AvlFeed[]),
  }
})
