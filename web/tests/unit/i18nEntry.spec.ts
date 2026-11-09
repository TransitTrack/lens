import { describe, expect, it } from 'vitest'
import { createI18n } from 'vue-i18n'
import { pluralRules } from '../../i18n/pluralRules'
import en from '../../i18n/locales/en.json'
import pl from '../../i18n/locales/pl.json'
import fr from '../../i18n/locales/fr.json'
import { deriveFeatures } from '../../app/utils/feedFeatures'
import { buildOverviewStats } from '../../app/utils/overviewStats'
import type { VehicleRow } from '../../app/types/vehicles'

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

describe('feed features (pl)', () => {
  const t = make('pl')

  it('translates derived feature labels', () => {
    const out = deriveFeatures({ filesPresent: ['shapes.txt'], rowCounts: {}, routes: [] }, t)
    expect(out.find((f) => f.key === 'shapes')?.label).toBe('Przebiegi tras')
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

describe('Polish plural forms of counted nouns', () => {
  const t = make('pl')
  const stops = (n: number) => t('feed.overview.stops', { count: n }, n)

  it('uses nominative plural for the few form and genitive for many', () => {
    expect(stops(1)).toBe('1 przystanek')
    expect(stops(2)).toBe('2 przystanki')
    expect(stops(5)).toBe('5 przystanków')
    expect(stops(22)).toBe('22 przystanki')
    expect(stops(12)).toBe('12 przystanków')
  })

  it('agrees the verb in reportingOk', () => {
    expect(t('overviewStats.reportingOk', { count: 2 }, 2)).toBe('2 zgłaszają poprawny stan')
    expect(make('fr')('overviewStats.reportingOk', { count: 2 }, 2)).toBe('2 signalent un état OK')
  })
})
