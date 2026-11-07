import { createSharedComposable } from '@vueuse/core'
import { AvlFeedCatalogApi, GTFSApi } from '~~/generated/rest'
import type { AvlFeed, Feed } from '~~/generated/rest'
import { fetchAllPages } from '~/utils/restPagination'

/** Shared polling for feed selection; callers reuse one pair of requests and timers. */
export const useRestFeedCatalog = createSharedComposable(() => {
  const gtfsFeeds = shallowRef<Feed[]>([])
  const avlFeeds = shallowRef<AvlFeed[]>([])
  const abort = new AbortController()
  const gtfsApi = new GTFSApi()
  const avlApi = new AvlFeedCatalogApi()
  let gtfsTimer: ReturnType<typeof setTimeout> | undefined
  let avlTimer: ReturnType<typeof setTimeout> | undefined

  const pollGtfs = async () => {
    try {
      const data = await fetchAllPages((offset, limit) => gtfsApi.feedsRaw({ offset, limit }, { signal: abort.signal }))
      if (!abort.signal.aborted) gtfsFeeds.value = data
    } catch {
      // Keep the last successful catalog until the next poll.
    } finally {
      if (!abort.signal.aborted) gtfsTimer = setTimeout(pollGtfs, 120_000)
    }
  }
  const pollAvl = async () => {
    try {
      const data = await fetchAllPages((offset, limit) => avlApi.avlFeedsRaw({ offset, limit }, { signal: abort.signal }))
      if (!abort.signal.aborted) avlFeeds.value = data
    } catch {
      // Keep the last successful catalog until the next poll.
    } finally {
      if (!abort.signal.aborted) avlTimer = setTimeout(pollAvl, 60_000)
    }
  }
  onMounted(() => { void pollGtfs(); void pollAvl() })
  onScopeDispose(() => {
    abort.abort()
    if (gtfsTimer) clearTimeout(gtfsTimer)
    if (avlTimer) clearTimeout(avlTimer)
  })
  return { gtfsFeeds, avlFeeds }
})
