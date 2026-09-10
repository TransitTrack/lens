export type UnitSystem = 'metric' | 'imperial'

const MPS_TO_KMH = 3.6
const MPS_TO_MPH = 2.236936
const M_TO_FT = 3.280839895
const M_TO_MI = 1 / 1609.344

/** Speed from metres/second → a display string in the chosen system. */
export function formatSpeed(mps: number | null | undefined, system: UnitSystem): string {
  if (mps == null || !Number.isFinite(mps)) return '—'
  return system === 'imperial'
    ? `${Math.round(mps * MPS_TO_MPH)} mph`
    : `${Math.round(mps * MPS_TO_KMH)} km/h`
}

/**
 * Distance in metres → a display string. Small values keep their fine unit
 * (m / ft); larger values switch to km / mi with one decimal.
 */
export function formatDistance(m: number | null | undefined, system: UnitSystem): string {
  if (m == null || !Number.isFinite(m)) return '—'
  if (system === 'imperial') {
    const ft = m * M_TO_FT
    return ft < 528 ? `${Math.round(ft)} ft` : `${(m * M_TO_MI).toFixed(1)} mi`
  }
  return m < 1000 ? `${Math.round(m)} m` : `${(m / 1000).toFixed(1)} km`
}
