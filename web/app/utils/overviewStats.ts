import type { VehicleRow } from '../composables/useVehiclePolling'
import type { Bar } from './chart'

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

export function buildOverviewStats(vehicles: VehicleRow[], feeds: FeedLike[]): OverviewStat[] {
  const tracked = vehicles.length
  const matched = vehicles.filter((v) => v.matched && !v.stale).length
  const stale = vehicles.filter((v) => v.stale).length
  const matchedPct = tracked === 0 ? 0 : Math.round((matched / tracked) * 100)
  const activeFeeds = feeds.filter((f) => f.enabled).length
  const healthyFeeds = feeds.filter((f) => /ok|success|healthy/i.test(f.lastPollStatus ?? '')).length

  return [
    { label: 'Vehicles tracked', value: String(tracked), icon: 'i-lucide-bus' },
    {
      label: 'Matched to a trip',
      value: `${matchedPct}%`,
      icon: 'i-lucide-route',
      hint: `${matched} of ${tracked}`,
    },
    { label: 'Stale positions', value: String(stale), icon: 'i-lucide-clock-alert' },
    {
      label: 'Active feeds',
      value: String(activeFeeds),
      icon: 'i-lucide-rss',
      hint: `${healthyFeeds} reporting OK`,
    },
  ]
}

const ADHERENCE_BINS: { label: string; test: (s: number) => boolean; color: string }[] = [
  { label: '≥5m early', test: (s) => s <= -300, color: '#d97706' },
  { label: '2–5m early', test: (s) => s > -300 && s <= -120, color: '#f59e0b' },
  { label: '1–2m early', test: (s) => s > -120 && s <= -30, color: '#fbbf24' },
  { label: 'On time', test: (s) => s > -30 && s < 60, color: '#22c55e' },
  { label: '1–2m late', test: (s) => s >= 60 && s < 120, color: '#f87171' },
  { label: '2–5m late', test: (s) => s >= 120 && s < 300, color: '#ef4444' },
  { label: '≥5m late', test: (s) => s >= 300, color: '#b91c1c' },
]

/** Distribution of schedule adherence across the fleet, as chart bars. */
export function adherenceHistogram(vehicles: VehicleRow[]): Bar[] {
  const secs = vehicles
    .map((v) => v.scheduleAdherenceSec)
    .filter((s): s is number => s != null)
  return ADHERENCE_BINS.map((bin) => {
    const value = secs.filter((s) => bin.test(s)).length
    return { label: bin.label, value, color: bin.color, hint: `${bin.label}: ${value} vehicles` }
  })
}
