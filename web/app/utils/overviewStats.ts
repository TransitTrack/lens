import type { VehicleRow } from '../composables/useVehiclePolling'
import type { Bar } from './chart'

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
      hint: t('overviewStats.reportingOk', { count: healthyFeeds }),
    },
  ]
}

// i18n-keys: overviewStats.bin.*
const ADHERENCE_BINS: { labelKey: string; test: (s: number) => boolean; color: string }[] = [
  { labelKey: 'overviewStats.bin.early5', test: (s) => s <= -300, color: '#d97706' },
  { labelKey: 'overviewStats.bin.early2to5', test: (s) => s > -300 && s <= -120, color: '#f59e0b' },
  { labelKey: 'overviewStats.bin.early1to2', test: (s) => s > -120 && s <= -30, color: '#fbbf24' },
  { labelKey: 'overviewStats.bin.onTime', test: (s) => s > -30 && s < 60, color: '#22c55e' },
  { labelKey: 'overviewStats.bin.late1to2', test: (s) => s >= 60 && s < 120, color: '#f87171' },
  { labelKey: 'overviewStats.bin.late2to5', test: (s) => s >= 120 && s < 300, color: '#ef4444' },
  { labelKey: 'overviewStats.bin.late5', test: (s) => s >= 300, color: '#b91c1c' },
]

/** Distribution of schedule adherence across the fleet, as chart bars. */
export function adherenceHistogram(vehicles: VehicleRow[], t: Translate): Bar[] {
  const secs = vehicles
    .map((v) => v.scheduleAdherenceSec)
    .filter((s): s is number => s != null)
  return ADHERENCE_BINS.map((bin) => {
    const value = secs.filter((s) => bin.test(s)).length
    const label = t(bin.labelKey)
    return {
      label,
      value,
      color: bin.color,
      hint: t('overviewStats.binHint', { label, count: value }, value),
    }
  })
}
