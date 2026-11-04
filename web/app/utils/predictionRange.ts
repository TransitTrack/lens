type Translate = (key: string, params?: Record<string, unknown>, plural?: number) => string

export interface RangeInput {
  /** ISO-8601 predicted arrival. */
  predictedArrival?: string | null
  /** Signed seconds relative to predictedArrival (<= 0). */
  rangeLowSec?: number | null
  /** Signed seconds relative to predictedArrival (>= 0). */
  rangeHighSec?: number | null
}

export interface ArrivalRange {
  unit: 'sec' | 'min'
  /** Time until the predicted arrival, in `unit`. */
  eta: number
  /** Earliest likely arrival from now, in `unit` (never below 0). */
  low: number
  /** Latest likely arrival from now, in `unit`. */
  high: number
}

/** Below this many seconds to the range's upper end, the range is given in seconds. */
const SECONDS_BELOW = 120

/**
 * The predicted arrival and its range (about an 80% interval) as offsets from now.
 * Null when there is no predicted arrival, no range, or the whole range is already past.
 * Values are in seconds while the range ends within two minutes, otherwise in whole
 * minutes (each end rounded to the nearest minute). Nothing is reported before now.
 */
export function arrivalRange(input: RangeInput, nowMs: number): ArrivalRange | null {
  const { predictedArrival, rangeLowSec, rangeHighSec } = input
  if (!predictedArrival || rangeLowSec == null || rangeHighSec == null) return null
  const arrivalMs = Date.parse(predictedArrival)
  if (!Number.isFinite(arrivalMs)) return null
  const etaSec = (arrivalMs - nowMs) / 1000
  const highSec = Math.max(0, etaSec + rangeHighSec)
  if (highSec <= 0) return null
  const lowSec = Math.max(0, etaSec + rangeLowSec)
  const clampedEta = Math.max(0, etaSec)
  if (highSec < SECONDS_BELOW) {
    return {
      unit: 'sec',
      eta: Math.round(clampedEta),
      low: Math.round(lowSec),
      high: Math.round(highSec),
    }
  }
  return {
    unit: 'min',
    eta: Math.round(clampedEta / 60),
    low: Math.round(lowSec / 60),
    high: Math.round(highSec / 60),
  }
}

/**
 * Human text for a predicted arrival with its range, e.g. "4 min, likely 3 to 6" or
 * "40 s, likely 20 to 70"; just "4 min" when the range collapses to the ETA. Null when
 * [arrivalRange] is null, so a prediction without a range shows no range text.
 */
// i18n-keys: vehicles.detail.range.*
export function arrivalRangeLabel(input: RangeInput, nowMs: number, t: Translate): string | null {
  const range = arrivalRange(input, nowMs)
  if (!range) return null
  const unitKey = range.unit === 'sec' ? 'seconds' : 'minutes'
  if (range.low === range.high) {
    return t(`vehicles.detail.range.${unitKey}Exact`, { eta: range.eta })
  }
  return t(`vehicles.detail.range.${unitKey}`, { eta: range.eta, low: range.low, high: range.high })
}

export interface LateAssessmentInput {
  late?: boolean | null
  lateProbability?: number | null
}

export interface LateBadge {
  color: 'error' | 'success'
  /** "Late" or "On time": the badge never relies on its colour alone. */
  label: string
  /** "72% chance of being late", or null without a probability. */
  probability: string | null
}

/** Badge for a late assessment; null when there is no assessment (late is null). */
export function lateBadge(
  assessment: LateAssessmentInput | null | undefined,
  t: Translate,
): LateBadge | null {
  if (assessment?.late == null) return null
  const p = assessment.lateProbability
  return {
    color: assessment.late ? 'error' : 'success',
    label: assessment.late
      ? t('vehicles.detail.lateness.late')
      : t('vehicles.detail.lateness.onTime'),
    probability:
      p == null || !Number.isFinite(p)
        ? null
        : t('vehicles.detail.lateness.probability', { percent: `${Math.round(p * 100)}%` }),
  }
}
