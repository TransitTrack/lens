import { describe, expect, it } from 'vitest'
import { createI18n } from 'vue-i18n'
import { pluralRules } from '../../i18n/pluralRules'
import en from '../../i18n/locales/en.json'
import pl from '../../i18n/locales/pl.json'
import fr from '../../i18n/locales/fr.json'
import { routeTypeLabel } from '../../app/utils/gtfs'
import { routeTypeBreakdown } from '../../app/utils/feedFeatures'
import {
  ageLabel,
  coarseAgeLabel,
  currentStatusLabel,
  matchStatusLabel,
  occupancyLabel,
} from '../../app/utils/vehicleTelemetry'
import { VEHICLE_STATES, vehicleStateLabel } from '../../app/utils/vehicleFilters'

function make(locale: 'en' | 'pl' | 'fr') {
  return createI18n({
    legacy: false,
    locale,
    fallbackLocale: 'en',
    messages: { en, pl, fr },
    pluralRules,
  }).global
}

describe('route type labels', () => {
  const t = make('pl').t

  it('translates known types, collapses extended types and keeps unknown ones readable', () => {
    expect(routeTypeLabel(0, t)).toBe('Tramwaj')
    expect(routeTypeLabel(3, t)).toBe('Autobus')
    expect(routeTypeLabel(700, t)).toBe('Kolej linowo-terenowa')
    expect(routeTypeLabel(1500, t)).toBe('Typ 1500')
    expect(routeTypeLabel(null, t)).toBe('—')
  })

  it('keeps English wording', () => {
    const en = make('en').t
    expect(routeTypeLabel(11, en)).toBe('Trolleybus')
    expect(routeTypeLabel(1500, en)).toBe('Type 1500')
    expect(routeTypeBreakdown([{ routeType: 3 }, { routeType: 3 }, { routeType: 0 }], en)).toEqual([
      { type: 3, label: 'Bus', count: 2 },
      { type: 0, label: 'Tram', count: 1 },
    ])
  })
})

describe('vehicle enum labels', () => {
  const t = make('pl').t

  it('translates known values and falls back to the raw value', () => {
    expect(currentStatusLabel('STOPPED_AT', t)).toBe('Stoi na przystanku')
    expect(currentStatusLabel('MYSTERY', t)).toBe('MYSTERY')
    expect(occupancyLabel('FULL', t)).toBe('Pełny')
    expect(occupancyLabel('MYSTERY', t)).toBe('MYSTERY')
    expect(matchStatusLabel('SKIPPED', t)).toBe('Pominięty')
    expect(matchStatusLabel('MYSTERY', t)).toBe('MYSTERY')
  })

  it('translates every vehicle state in every locale', () => {
    for (const locale of ['en', 'pl', 'fr'] as const) {
      const lt = make(locale).t
      for (const state of VEHICLE_STATES) {
        expect(vehicleStateLabel(state.value, lt)).not.toContain('vehicles.state')
      }
    }
    expect(vehicleStateLabel('late', t)).toBe('Opóźnione')
  })

  it('keeps English raw occupancy values unchanged', () => {
    expect(occupancyLabel('MANY_SEATS_AVAILABLE', make('en').t)).toBe('MANY_SEATS_AVAILABLE')
  })
})

describe('report age', () => {
  it('keeps English wording', () => {
    const t = make('en').t
    expect(ageLabel(12, t)).toBe('12s ago')
    expect(ageLabel(180, t)).toBe('3m ago')
    expect(ageLabel(7200, t)).toBe('2h ago')
    expect(coarseAgeLabel(10, t)).toBe('a few seconds ago')
    expect(coarseAgeLabel(180, t)).toBe('3m ago')
  })

  it('translates in Polish and French', () => {
    expect(ageLabel(180, make('pl').t)).toBe('3 min temu')
    expect(coarseAgeLabel(10, make('pl').t)).toBe('kilka sekund temu')
    expect(ageLabel(7200, make('fr').t)).toBe('il y a 2 h')
  })
})

describe('vehicle plurals', () => {
  const stops = (locale: 'en' | 'pl' | 'fr', n: number) =>
    make(locale).t('vehicles.detail.stopsCount', { count: n }, n)

  it('keeps English singular and plural', () => {
    expect(stops('en', 1)).toBe('1 stop')
    expect(stops('en', 5)).toBe('5 stops')
  })

  it('uses the three Polish forms', () => {
    expect(stops('pl', 1)).toBe('1 przystanek')
    expect(stops('pl', 3)).toBe('3 przystanki')
    expect(stops('pl', 12)).toBe('12 przystanków')
    expect(stops('pl', 22)).toBe('22 przystanki')
    expect(stops('pl', 5)).toBe('5 przystanków')
  })

  it('treats 0 and 1 as singular in French', () => {
    expect(stops('fr', 0)).toBe('0 arrêt')
    expect(stops('fr', 2)).toBe('2 arrêts')
  })

  it('formats "shown of total" with the total as plural count', () => {
    const shown = (locale: 'en' | 'pl' | 'fr', total: number) =>
      make(locale).t('vehicles.shownOf', { shown: '1', total: String(total) }, total)
    expect(shown('en', 150)).toBe('1 of 150 vehicles')
    expect(shown('pl', 1)).toBe('1 z 1 pojazdu')
    expect(shown('pl', 3)).toBe('1 z 3 pojazdów')
  })
})
