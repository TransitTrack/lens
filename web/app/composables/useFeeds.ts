import { useFeedsQuery, useAvlFeedsQuery } from '../../generated/graphql'

export interface AnnotatedFeed {
  code: string
  name: string
  description: string | null
  enabled: boolean
  /** linked AVL (realtime) feed code, if any */
  avlFeedCode: string | null
  hasRealtime: boolean
  /** last-poll status of the linked AVL feed */
  realtimeStatus: string | null
  realtimeHealthy: boolean
}

function isHealthy(status: string | null | undefined): boolean {
  return /ok|success|healthy/i.test(status ?? '')
}

/**
 * Feed selection is URL-driven: the `feedCode` route param is the selected GTFS
 * feed. This composable resolves it against the GTFS feed list and its linked
 * AVL (realtime) feed.
 */
export function useFeeds() {
  const route = useRoute()

  const { result: gtfsResult } = useFeedsQuery(() => ({ pollInterval: 120_000 }))
  const { result: avlResult } = useAvlFeedsQuery(() => ({ pollInterval: 60_000 }))

  const feeds = computed<AnnotatedFeed[]>(() => {
    const avl = avlResult.value?.avlFeeds ?? []
    return (gtfsResult.value?.feeds ?? []).map((f) => {
      const linked = avl.find((a) => a.gtfsFeedCode === f.code) ?? null
      return {
        code: f.code,
        name: f.name,
        description: f.description ?? null,
        enabled: f.enabled,
        avlFeedCode: linked?.code ?? null,
        hasRealtime: !!linked,
        realtimeStatus: linked?.lastPollStatus ?? null,
        realtimeHealthy: !!linked && isHealthy(linked.lastPollStatus),
      }
    })
  })

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
