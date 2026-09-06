import { useAvlFeedsQuery } from '../../generated/graphql'

function isHealthy(status: string | null | undefined): boolean {
  return /ok|success|healthy/i.test(status ?? '')
}

/**
 * Surfaces feed-health problems as toasts. Call once (from the default layout).
 * A feed only toasts on the transition into an unhealthy state, and a recovery
 * toast fires when it goes healthy again.
 */
export function useFeedAlerts() {
  const toast = useToast()
  const { result, error } = useAvlFeedsQuery(() => ({ pollInterval: 60_000 }))

  const unhealthy = new Set<string>()
  let queryErrored = false

  watch(
    () => result.value?.avlFeeds,
    (feeds) => {
      for (const feed of feeds ?? []) {
        if (!feed.enabled) continue
        const healthy = isHealthy(feed.lastPollStatus)
        if (!healthy && !unhealthy.has(feed.code)) {
          unhealthy.add(feed.code)
          toast.add({
            title: `${feed.name}: feed unhealthy`,
            description: `Last poll status: ${feed.lastPollStatus ?? 'unknown'}`,
            color: 'warning',
            icon: 'i-lucide-triangle-alert',
          })
        } else if (healthy && unhealthy.has(feed.code)) {
          unhealthy.delete(feed.code)
          toast.add({
            title: `${feed.name}: feed recovered`,
            color: 'success',
            icon: 'i-lucide-check',
          })
        }
      }
    },
    { deep: true },
  )

  watch(error, (err) => {
    if (err && !queryErrored) {
      queryErrored = true
      toast.add({
        title: 'Cannot reach the backend',
        description: err.message,
        color: 'error',
        icon: 'i-lucide-wifi-off',
      })
    } else if (!err) {
      queryErrored = false
    }
  })
}
