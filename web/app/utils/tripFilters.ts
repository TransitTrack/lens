/** Seconds-of-day → "HH:MM" (24h+ clock kept, e.g. 25:30 for after-midnight). */
export function formatHm(sec: number | null | undefined): string {
  if (sec == null || !Number.isFinite(sec)) return '—'
  const h = Math.floor(sec / 3600)
  const m = Math.floor((sec % 3600) / 60)
  return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`
}

/** "H:MM" / "HH:MM" → seconds of day. `null` if unparseable. */
export function parseHm(value: string | null | undefined): number | null {
  if (!value) return null
  const m = /^(\d{1,2}):(\d{2})$/.exec(value.trim())
  if (!m) return null
  const h = Number(m[1])
  const min = Number(m[2])
  if (min > 59) return null
  return h * 3600 + min * 60
}

export interface TimePreset {
  key: string
  label: string
  /** inclusive lower bound, seconds of day */
  fromSec: number | null
  /** exclusive upper bound, seconds of day; `null` = open-ended */
  toSec: number | null
}

const H = (n: number) => n * 3600

export const TIME_PRESETS: TimePreset[] = [
  { key: 'all', label: 'All day', fromSec: null, toSec: null },
  { key: 'early', label: 'Early', fromSec: H(0), toSec: H(6) },
  { key: 'am', label: 'AM peak', fromSec: H(6), toSec: H(10) },
  { key: 'midday', label: 'Midday', fromSec: H(10), toSec: H(15) },
  { key: 'pm', label: 'PM peak', fromSec: H(15), toSec: H(19) },
  { key: 'evening', label: 'Evening', fromSec: H(19), toSec: H(24) },
  { key: 'late', label: 'Late (24h+)', fromSec: H(24), toSec: null },
]

/**
 * Is a trip's start inside `[fromSec, toSec)`? A `null` bound is open on that
 * side, so `(null, null)` matches everything.
 */
export function inWindow(
  startSec: number | null | undefined,
  fromSec: number | null,
  toSec: number | null,
): boolean {
  if (fromSec == null && toSec == null) return true
  if (startSec == null) return false
  if (fromSec != null && startSec < fromSec) return false
  if (toSec != null && startSec >= toSec) return false
  return true
}
