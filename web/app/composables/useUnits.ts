import { formatSpeed, formatDistance, type UnitSystem } from '~/utils/units'

/**
 * UI measurement system, from `app.config.ts` (`units: 'metric' | 'imperial'`).
 * Exposes formatters so callers never hard-code km/h or metres.
 */
export function useUnits() {
  const cfg = useAppConfig()
  const system = computed<UnitSystem>(() => (cfg.units === 'imperial' ? 'imperial' : 'metric'))

  return {
    system,
    speed: (mps: number | null | undefined) => formatSpeed(mps, system.value),
    distance: (m: number | null | undefined) => formatDistance(m, system.value),
  }
}
