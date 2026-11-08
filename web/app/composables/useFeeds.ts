import { useRestFeedCatalog } from './useRestFeedCatalog'
import { annotateFeeds } from '~/utils/joins/feeds'
import type { AnnotatedFeed } from '~/utils/joins/feeds'

export type { AnnotatedFeed }

/**
 * Feed selection is URL-driven: the `feedCode` route param is the selected GTFS
 * feed. This composable resolves it against the GTFS feed list and its linked
 * AVL (realtime) feed.
 */
export function useFeeds() {
  const route = useRoute()

  const { gtfsFeeds, avlFeeds } = useRestFeedCatalog()

  const feeds = computed<AnnotatedFeed[]>(() => annotateFeeds(gtfsFeeds.value, avlFeeds.value))

  const selectedFeedCode = computed(() => {
    const p = route.params.feedCode
    return typeof p === 'string' && p ? p : null
  })

  const selectedFeed = computed(
    () => feeds.value.find((f) => f.code === selectedFeedCode.value) ?? null,
  )

  const selectedAvlFeedCode = computed(() => selectedFeed.value?.avlFeedCode ?? null)

  /** true once feeds have loaded and the URL feed is not among them */
  const feedIsUnknown = computed(
    () =>
      !!selectedFeedCode.value &&
      feeds.value.length > 0 &&
      !feeds.value.some((f) => f.code === selectedFeedCode.value),
  )

  /** Build a path within the current feed, e.g. feedPath('/vehicles'). */
  function feedPath(sub = ''): string {
    return `/${selectedFeedCode.value ?? ''}${sub}`
  }

  return {
    feeds,
    selectedFeedCode,
    selectedFeed,
    selectedAvlFeedCode,
    feedIsUnknown,
    feedPath,
  }
}
