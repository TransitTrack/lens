import type {VehicleRow} from '~/composables/useVehiclePolling'
import type {AvlFeedsQuery} from '~~/generated/graphql'

export interface NetworkPulseSignal {
  key: 'onTime' | 'attention' | 'coverage'
  label: string
  value: string
  detail: string
  icon: string
  color: 'success' | 'warning' | 'neutral'
}

export type AvlFeedHealth = Pick<
  AvlFeedsQuery['avlFeeds'][number],
  'code' | 'enabled' | 'lastPollStatus'
>

function percentage(value: number, total: number): string {
  return `${Math.round((value / total) * 100)}%`
}

export function buildNetworkPulseSignals(
  vehicles: VehicleRow[],
  feeds: AvlFeedHealth[],
  selectedAvlFeedCode: string | null,
): NetworkPulseSignal[] {
  const tracked = vehicles.length

  const selectedFeed = feeds.find((feed) => feed.code === selectedAvlFeedCode)
  const hasHealthySelectedFeed =
    !!selectedFeed?.enabled && /ok|success|healthy/i.test(selectedFeed.lastPollStatus ?? '')

  if (tracked === 0 || !hasHealthySelectedFeed) {
    const detail =
      tracked === 0
        ? 'No vehicles available'
        : selectedFeed
          ? 'Selected realtime source is not reporting healthy'
          : 'Selected realtime source unavailable'
    return [
      {
        key: 'onTime',
        label: 'On-time performance',
        value: '—',
        detail,
        icon: 'i-lucide-clock-3',
        color: 'neutral',
      },
      {
        key: 'attention',
        label: 'Needs attention',
        value: '—',
        detail,
        icon: 'i-lucide-triangle-alert',
        color: 'neutral',
      },
      {
        key: 'coverage',
        label: 'Realtime coverage',
        value: '—',
        detail,
        icon: 'i-lucide-radio',
        color: 'neutral',
      },
    ]
  }

  const reporting = vehicles.filter((vehicle) => !vehicle.stale).length
  const matched = vehicles.filter((vehicle) => vehicle.matched && !vehicle.stale)
  const adherenceAvailable = matched.filter((vehicle) => vehicle.scheduleAdherenceSec != null)
  const onTime = adherenceAvailable.filter((vehicle) => {
    const adherence = vehicle.scheduleAdherenceSec
    return adherence != null && adherence > -30 && adherence < 60
  }).length
  const attention = vehicles.filter((vehicle) => vehicle.stale || !vehicle.matched).length
  const onTimeAvailable = adherenceAvailable.length > 0

  return [
    {
      key: 'onTime',
      label: 'On-time performance',
      value: onTimeAvailable ? percentage(onTime, adherenceAvailable.length) : '—',
      detail: onTimeAvailable
        ? `${onTime} of ${adherenceAvailable.length} matched vehicle${adherenceAvailable.length === 1 ? '' : 's'} within 1 minute`
        : 'No matched vehicles with adherence available',
      icon: 'i-lucide-clock-3',
      color: onTimeAvailable ? 'success' : 'neutral',
    },
    {
      key: 'attention',
      label: 'Needs attention',
      value: String(attention),
      detail: attention === 1 ? 'stale or unmatched vehicle' : `${attention} stale or unmatched vehicles`,
      icon: 'i-lucide-triangle-alert',
      color: attention > 0 ? 'warning' : 'success',
    },
    {
      key: 'coverage',
      label: 'Realtime coverage',
      value: percentage(reporting, tracked),
      detail: `${reporting} of ${tracked} vehicle${tracked === 1 ? '' : 's'} reporting`,
      icon: 'i-lucide-radio',
      color: 'success',
    },
  ]
}
