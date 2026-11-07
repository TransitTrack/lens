import { describe, expect, it } from 'vitest'
import { createI18n } from 'vue-i18n'
import { formatTimeAgo } from '@vueuse/core'
import { pluralRules } from '../../i18n/pluralRules'
import en from '../../i18n/locales/en.json'
import pl from '../../i18n/locales/pl.json'
import fr from '../../i18n/locales/fr.json'
import ro from '../../i18n/locales/ro.json'
import { displayLocale, formatNumber, intlLocale } from '../../app/utils/intlLocale'
import { formatClock, formatDottedTimestamp } from '../../app/utils/clock'
import { formatReportTimestamp } from '../../app/utils/avlReport'
import { gtfsDate } from '../../app/utils/gtfs'
import { timeAgoMessages } from '../../app/utils/timeAgo'

function make(locale: 'en' | 'pl' | 'fr' | 'ro') {
  return createI18n({
    legacy: false,
    locale,
    fallbackLocale: 'en',
    messages: { en, pl, fr, ro },
    pluralRules,
  }).global
}

const instant = '2026-11-03T16:40:09Z' // Tuesday; 17:40:09 in Warsaw (CET, +1)

describe('intlLocale', () => {
  it('maps app locales to regional tags, English stays 24h / day-first', () => {
    expect(intlLocale('en')).toBe('en-GB')
    expect(intlLocale('pl')).toBe('pl-PL')
    expect(intlLocale('fr')).toBe('fr-FR')
    expect(intlLocale(undefined)).toBe('en-GB')
    expect(displayLocale('en')).toBeUndefined()
    expect(displayLocale('pl')).toBe('pl-PL')
  })
})

describe('formatClock', () => {
  it('keeps English output', () => {
    expect(formatClock(instant, { timeZone: 'Europe/Warsaw', locale: 'en' })).toBe('17:40')
    expect(formatClock(instant, { timeZone: 'Europe/Warsaw', seconds: true, locale: 'en' })).toBe('17:40:09')
    expect(formatClock(instant, { timeZone: 'Europe/Warsaw', seconds: true })).toBe('17:40:09')
  })
  it('stays 24-hour in pl and fr', () => {
    expect(formatClock(instant, { timeZone: 'Europe/Warsaw', seconds: true, locale: 'pl' })).toBe('17:40:09')
    expect(formatClock(instant, { timeZone: 'Europe/Warsaw', locale: 'fr' })).toBe('17:40')
  })
})

describe('formatReportTimestamp', () => {
  it('keeps English output', () => {
    expect(formatReportTimestamp(instant, 'Europe/Warsaw', 'en')).toBe('03/11/2026, 17:40:09')
    expect(formatReportTimestamp(instant, 'Europe/Warsaw')).toBe('03/11/2026, 17:40:09')
  })
  it('follows the locale in pl and fr', () => {
    expect(formatReportTimestamp(instant, 'Europe/Warsaw', 'pl')).toBe('03.11.2026, 17:40:09')
    expect(formatReportTimestamp(instant, 'Europe/Warsaw', 'fr')).toMatch(/^03\/11\/2026 17:40:09$/)
  })
})

describe('formatDottedTimestamp', () => {
  it('keeps the dd.mm.yyyy, hh:mm:ss shape in every locale', () => {
    const date = new Date(instant)
    const utc = (l: string) => formatDottedTimestamp(date, l, 'UTC')
    expect(utc('en')).toBe('03.11.2026, 16:40:09')
    expect(utc('pl')).toBe('03.11.2026, 16:40:09')
    expect(utc('fr')).toBe('03.11.2026, 16:40:09')
  })
  it('matches what the old hardcoded de-DE / en-GB formatters produced', () => {
    const old = (tag: string) => {
      const parts = new Intl.DateTimeFormat(tag, {
        day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23', timeZone: 'UTC',
      }).formatToParts(new Date(instant))
      const v = (type: string) => parts.find((p) => p.type === type)?.value ?? ''
      return `${v('day')}.${v('month')}.${v('year')}, ${v('hour')}:${v('minute')}:${v('second')}`
    }
    expect(formatDottedTimestamp(new Date(instant), 'en', 'UTC')).toBe(old('de-DE'))
    expect(formatDottedTimestamp(new Date(instant), 'en', 'UTC')).toBe(old('en-GB'))
  })
})

describe('gtfsDate', () => {
  const browserDefault = new Date('2026-03-05T12:00:00').toLocaleDateString(undefined, { dateStyle: 'medium' })
  it('keeps the browser-default English output', () => {
    expect(gtfsDate('20260305')).toBe(browserDefault)
    expect(gtfsDate('20260305', 'en')).toBe(browserDefault)
    expect(gtfsDate(null, 'en')).toBe('—')
    expect(gtfsDate('not-a-date', 'en')).toBe('not-a-date')
  })
  it('localises in pl and fr', () => {
    expect(gtfsDate('20260305', 'pl')).toBe('5 mar 2026')
    expect(gtfsDate('20260305', 'fr')).toBe('5 mars 2026')
  })
})

describe('formatNumber', () => {
  it('keeps the browser-default English grouping', () => {
    expect(formatNumber(1234567, 'en')).toBe((1234567).toLocaleString())
  })
  it('uses pl/fr separators', () => {
    expect(formatNumber(1234567.5, 'pl').replace(/\s/g, ' ')).toBe('1 234 567,5')
    expect(formatNumber(1234567.5, 'fr').replace(/\s/g, ' ')).toBe('1 234 567,5')
  })
  it('uses ro separators', () => {
    expect(intlLocale('ro')).toBe('ro-RO')
    expect(formatNumber(1234567.5, 'ro')).toBe('1.234.567,5')
  })
})

describe('timeAgoMessages', () => {
  const now = Date.parse('2026-11-03T12:00:00Z')
  const ago = (ms: number, locale: 'en' | 'pl' | 'fr', future = false) => {
    const t = make(locale).t as never
    return formatTimeAgo(new Date(future ? now + ms : now - ms), { messages: timeAgoMessages(t) }, now)
  }
  const reference = (ms: number, future = false) =>
    formatTimeAgo(new Date(future ? now + ms : now - ms), {}, now)
  const MIN = 60_000
  const HOUR = 3_600_000
  const DAY = 86_400_000

  it('equals the vueuse default English strings exactly', () => {
    const samples = [
      10_000, MIN, 5 * MIN, 59 * MIN, HOUR, 3 * HOUR, 23 * HOUR, DAY, 2 * DAY, 6 * DAY,
      7 * DAY, 14 * DAY, 28 * DAY, 30 * DAY, 60 * DAY, 200 * DAY, 400 * DAY, 800 * DAY,
    ]
    for (const ms of samples) {
      expect(ago(ms, 'en')).toBe(reference(ms))
      expect(ago(ms, 'en', true)).toBe(reference(ms, true))
    }
    expect(ago(3 * HOUR, 'en')).toBe('3 hours ago')
    expect(ago(DAY, 'en')).toBe('yesterday')
    expect(ago(DAY, 'en', true)).toBe('tomorrow')
  })

  it('translates to Polish with one/few/many forms', () => {
    expect(ago(10_000, 'pl')).toBe('przed chwilą')
    expect(ago(HOUR, 'pl')).toBe('1 godzina temu')
    expect(ago(3 * HOUR, 'pl')).toBe('3 godziny temu')
    expect(ago(5 * HOUR, 'pl')).toBe('5 godzin temu')
    expect(ago(22 * MIN, 'pl')).toBe('22 minuty temu')
    expect(ago(DAY, 'pl')).toBe('wczoraj')
    expect(ago(2 * DAY, 'pl', true)).toBe('za 2 dni')
  })

  it('translates to French', () => {
    expect(ago(10_000, 'fr')).toBe("à l'instant")
    expect(ago(3 * HOUR, 'fr')).toBe('il y a 3 heures')
    expect(ago(DAY, 'fr')).toBe('hier')
    expect(ago(2 * DAY, 'fr', true)).toBe('dans 2 jours')
  })
})
