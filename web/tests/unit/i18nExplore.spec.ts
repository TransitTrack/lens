import { describe, expect, it } from 'vitest'
import { createI18n } from 'vue-i18n'
import { pluralRules } from '../../i18n/pluralRules'
import en from '../../i18n/locales/en.json'
import pl from '../../i18n/locales/pl.json'
import fr from '../../i18n/locales/fr.json'
import { serviceDaysLabel } from '../../app/utils/gtfs'
import { layoverLabel } from '../../app/utils/blocks'
import { departureHistogram } from '../../app/utils/stopSchedule'

function make(locale: 'en' | 'pl' | 'fr') {
  return createI18n({
    legacy: false,
    locale,
    fallbackLocale: 'en',
    messages: { en, pl, fr },
    pluralRules,
  }).global
}

const allDays = {
  monday: true, tuesday: true, wednesday: true, thursday: true, friday: true, saturday: true, sunday: true,
}
const noDays = {
  monday: false, tuesday: false, wednesday: false, thursday: false, friday: false, saturday: false, sunday: false,
}

describe('service day labels', () => {
  it('keeps English wording', () => {
    const t = make('en').t
    expect(serviceDaysLabel(allDays, t)).toBe('Every day')
    expect(serviceDaysLabel({ ...noDays, monday: true, wednesday: true }, t)).toBe('mon, wed')
    expect(serviceDaysLabel({ ...noDays, saturday: true, sunday: true }, t)).toBe('Weekends')
  })

  it('translates to Polish', () => {
    const t = make('pl').t
    expect(serviceDaysLabel(allDays, t)).toBe('Codziennie')
    expect(serviceDaysLabel({ ...noDays, monday: true, wednesday: true }, t)).toBe('pon, śr')
  })
})

describe('block layover label', () => {
  it('translates in Polish', () => {
    expect(layoverLabel(600, true, make('pl').t)).toBe('postój 10 min · następnie przejazd techniczny')
  })
})

describe('departure histogram hints', () => {
  const serving = [{ pattern: { patternKey: 'a' }, stopPathIndex: 0, offsetSec: 0 }] as never
  const kinds = new Map([['svc', new Set(['weekday' as const])]])
  const trips = (n: number) => [
    { patternKey: 'a', trips: Array.from({ length: n }, () => ({ startTimeSec: 3600 * 8, serviceId: 'svc' })) },
  ]

  it('uses Polish plural forms for departure counts', () => {
    const t = make('pl').t
    const hint = (n: number) => departureHistogram(serving, trips(n), kinds, 'weekday', t).bars[8]!.hint
    expect(hint(1)).toBe('8:00–9:00 · 1 odjazd')
    expect(hint(3)).toBe('8:00–9:00 · 3 odjazdy')
    expect(hint(5)).toBe('8:00–9:00 · 5 odjazdów')
  })

  it('keeps English output', () => {
    const t = make('en').t
    expect(departureHistogram(serving, trips(2), kinds, 'weekday', t).bars[8]!.hint).toBe('8:00–9:00 · 2 departures')
  })
})
