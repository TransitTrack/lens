import type {VehicleRow} from '~/composables/useVehiclePolling'
import type {AvlFeedsQuery} from '~~/generated/graphql'

type Translate = (key: string, params?: Record<string, unknown>, plural?: number) => string

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
  t: Translate,
): NetworkPulseSignal[] {
  const tracked = vehicles.length

  const selectedFeed = feeds.find((feed) => feed.code === selectedAvlFeedCode)
  const hasHealthySelectedFeed =
    !!selectedFeed?.enabled && selectedFeed.lastPollStatus === 'OK'

  if (tracked === 0 || !hasHealthySelectedFeed) {
    const detail =
      tracked === 0
        ? t('pulse.noVehicles')
        : selectedFeed
          ? t('pulse.sourceUnhealthy')
          : t('pulse.sourceUnavailable')
    return [
      {
        key: 'onTime',
        label: t('pulse.onTime'),
        value: '—',
        detail,
        icon: 'i-lucide-clock-3',
        color: 'neutral',
      },
      {
        key: 'attention',
        label: t('pulse.needsAttention'),
        value: '—',
        detail,
        icon: 'i-lucide-triangle-alert',
        color: 'neutral',
      },
      {
        key: 'coverage',
        label: t('pulse.coverage'),
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
      label: t('pulse.onTime'),
      value: onTimeAvailable ? percentage(onTime, adherenceAvailable.length) : '—',
      detail: onTimeAvailable
        ? t(
            'pulse.onTimeDetail',
            { onTime, total: adherenceAvailable.length },
            adherenceAvailable.length,
          )
        : t('pulse.noAdherence'),
      icon: 'i-lucide-clock-3',
      color: onTimeAvailable ? 'success' : 'neutral',
    },
    {
      key: 'attention',
      label: t('pulse.needsAttention'),
      value: String(attention),
      detail: t('pulse.attentionDetail', { count: attention }, attention),
      icon: 'i-lucide-triangle-alert',
      color: attention > 0 ? 'warning' : 'success',
    },
    {
      key: 'coverage',
      label: t('pulse.coverage'),
      value: percentage(reporting, tracked),
      detail: t('pulse.coverageDetail', { reporting, tracked }, tracked),
      icon: 'i-lucide-radio',
      color: 'success',
    },
  ]
}
