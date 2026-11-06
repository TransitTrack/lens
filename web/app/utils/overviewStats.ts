import type { VehicleRow } from '~/composables/useVehiclePolling'

type Translate = (key: string, params?: Record<string, unknown>, plural?: number) => string

export interface OverviewStat {
  label: string
  value: string
  icon: string
  hint?: string
}

interface FeedLike {
  enabled: boolean
  lastPollStatus?: string | null
}

export function buildOverviewStats(
  vehicles: VehicleRow[],
  feeds: FeedLike[],
  t: Translate,
): OverviewStat[] {
  const tracked = vehicles.length
  const matched = vehicles.filter((v) => v.matched && !v.stale).length
  const stale = vehicles.filter((v) => v.stale).length
  const matchedPct = tracked === 0 ? 0 : Math.round((matched / tracked) * 100)
  const activeFeeds = feeds.filter((f) => f.enabled).length
  const healthyFeeds = feeds.filter((f) => /ok|success|healthy/i.test(f.lastPollStatus ?? '')).length

  return [
    { label: t('overviewStats.vehiclesTracked'), value: String(tracked), icon: 'i-lucide-bus' },
    {
      label: t('overviewStats.matchedToTrip'),
      value: `${matchedPct}%`,
      icon: 'i-lucide-route',
      hint: t('overviewStats.matchedOf', { matched, tracked }),
    },
    { label: t('overviewStats.stalePositions'), value: String(stale), icon: 'i-lucide-clock-alert' },
    {
      label: t('overviewStats.activeFeeds'),
      value: String(activeFeeds),
      icon: 'i-lucide-rss',
      hint: t('overviewStats.reportingOk', { count: healthyFeeds }, healthyFeeds),
    },
  ]
}

