import { describe, expect, it } from 'vitest'
import { createI18n } from 'vue-i18n'
import { pluralRules } from '../../i18n/pluralRules'
import en from '../../i18n/locales/en.json'
import pl from '../../i18n/locales/pl.json'
import fr from '../../i18n/locales/fr.json'
import { revisionStatusMeta, cronSummary } from '../../app/utils/feedStatus'
import { deriveFeatures } from '../../app/utils/feedFeatures'
import { buildNetworkPulseSignals } from '../../app/utils/networkPulse'
import { buildOverviewStats } from '../../app/utils/overviewStats'
import type { VehicleRow } from '../../app/composables/useVehiclePolling'

function make(locale: 'en' | 'pl' | 'fr') {
  return createI18n({
    legacy: false,
    locale,
    fallbackLocale: 'en',
    messages: { en, pl, fr },
    pluralRules,
  }).global.t
}

function vehicle(over: Partial<VehicleRow>): VehicleRow {
  return { matched: true, stale: false, scheduleAdherenceSec: 0, ...over } as VehicleRow
}

describe('feed status helpers (pl)', () => {
  const t = make('pl')

  it('translates known statuses and falls back to the raw value', () => {
    expect(revisionStatusMeta('VALIDATING', t).label).toBe('Walidacja')
    expect(revisionStatusMeta('MYSTERY', t).label).toBe('MYSTERY')
    expect(revisionStatusMeta(null, t).label).toBe('—')
  })

  it('translates cron summaries and keeps unknown crons raw', () => {
    expect(cronSummary('0 */15 * * * *', t)).toBe('co 15 min')
    expect(cronSummary('0 30 3 * * *', t)).toBe('codziennie o 03:30')
    expect(cronSummary(null, t)).toBe('brak harmonogramu')
    expect(cronSummary('0 0 12 * * MON-FRI', t)).toBe('0 0 12 * * MON-FRI')
  })

  it('translates derived feature labels', () => {
    const out = deriveFeatures({ filesPresent: ['shapes.txt'], rowCounts: {}, routes: [] }, t)
    expect(out.find((f) => f.key === 'shapes')?.label).toBe('Przebiegi tras')
  })
})

describe('network pulse plurals', () => {
  const rows = (n: number) => Array.from({ length: n }, () => vehicle({}))
  const feeds = [{ code: 'a', enabled: true, lastPollStatus: 'OK' }]
  const coverage = (locale: 'en' | 'pl' | 'fr', n: number) =>
    buildNetworkPulseSignals(rows(n), feeds, 'a', make(locale)).find((s) => s.key === 'coverage')!.detail

  it('keeps English wording', () => {
    expect(coverage('en', 1)).toBe('1 of 1 vehicle reporting')
    expect(coverage('en', 3)).toBe('3 of 3 vehicles reporting')
    const attention = buildNetworkPulseSignals(
      [vehicle({ stale: true })],
      feeds,
      'a',
      make('en'),
    ).find((s) => s.key === 'attention')!
    expect(attention.detail).toBe('stale or unmatched vehicle')
  })

  it('uses the three Polish forms', () => {
    expect(coverage('pl', 1)).toBe('1 z 1 pojazdu zgłasza pozycję')
    expect(coverage('pl', 5)).toBe('5 z 5 pojazdów zgłasza pozycję')
    const attention = (n: number) =>
      buildNetworkPulseSignals(
        Array.from({ length: n }, () => vehicle({ stale: true })),
        feeds,
        'a',
        make('pl'),
      ).find((s) => s.key === 'attention')!.detail
    expect(attention(3)).toBe('3 nieaktualne lub niedopasowane pojazdy')
    expect(attention(5)).toBe('5 nieaktualnych lub niedopasowanych pojazdów')
  })

  it('treats 0 and 1 as singular in French', () => {
    expect(coverage('fr', 1)).toBe('1 véhicule sur 1 transmet sa position')
    expect(coverage('fr', 2)).toBe('2 véhicules sur 2 transmettent leur position')
  })
})

describe('overview stats', () => {
  it('builds English labels identical to the source copy', () => {
    const stats = buildOverviewStats([vehicle({})], [{ enabled: true, lastPollStatus: 'OK' }], make('en'))
    expect(stats.map((s) => s.label)).toEqual([
      'Vehicles tracked',
      'Matched to a trip',
      'Stale positions',
      'Active feeds',
    ])
    expect(stats[1]!.hint).toBe('1 of 1')
    expect(stats[3]!.hint).toBe('1 reporting OK')
  })
})
