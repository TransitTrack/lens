import { formatTimeAgo } from '@vueuse/core'
import { timeAgoMessages } from '~/utils/timeAgo'

/** "3 hours ago" in the active language; the single entry point for vueuse formatTimeAgo. */
export function useRelativeTime() {
  const { t } = useI18n()
  return (value: string | number | Date): string =>
    formatTimeAgo(new Date(value), { messages: timeAgoMessages(t) })
}
