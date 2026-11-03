export type OptimizationDeltaDirection = 'earlier' | 'later' | 'unchanged'

type Translate = (key: string, params?: Record<string, unknown>) => string
const KNOWN_KINDS = ['TRIP_SHIFT', 'STOP_TIME']

// i18n-keys: optimize.kind.*
export function formatOptimizationKind(kind: string, t: Translate): string {
  return KNOWN_KINDS.includes(kind) ? t(`optimize.kind.${kind}`) : kind
}

export function formatSignedDuration(seconds: number, t: Translate): {text: string; direction: OptimizationDeltaDirection} {
  if (seconds === 0) return {text: t('optimize.noChange'), direction: 'unchanged'}

  const absolute = Math.abs(seconds)
  const minutes = Math.floor(absolute / 60)
  const remainder = absolute % 60
  const duration = minutes
    ? remainder
      ? t('optimize.duration.minSec', {m: minutes, s: remainder})
      : t('optimize.duration.min', {m: minutes})
    : t('optimize.duration.sec', {s: remainder})

  return {
    text: `${seconds > 0 ? '+' : '−'}${duration}`,
    direction: seconds > 0 ? 'later' : 'earlier',
  }
}
