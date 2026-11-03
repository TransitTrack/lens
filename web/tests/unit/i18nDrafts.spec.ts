import { describe, expect, it } from 'vitest'
import { createI18n } from 'vue-i18n'
import { pluralRules } from '../../i18n/pluralRules'
import en from '../../i18n/locales/en.json'
import pl from '../../i18n/locales/pl.json'
import fr from '../../i18n/locales/fr.json'
import { formatOptimizationKind, formatSignedDuration } from '../../app/utils/optimizationPresentation'

function make(locale: 'en' | 'pl' | 'fr') {
  return createI18n({ legacy: false, locale, fallbackLocale: 'en', messages: { en, pl, fr }, pluralRules }).global
}

describe('optimization labels', () => {
  it('translates kinds and durations, with a raw fallback for unknown kinds', () => {
    const t = make('pl').t
    expect(formatOptimizationKind('TRIP_SHIFT', t)).toBe('Przesunięcie kursu')
    expect(formatOptimizationKind('MYSTERY', t)).toBe('MYSTERY')
    expect(formatSignedDuration(75, t)).toEqual({ text: '+1 min 15 s', direction: 'later' })
    expect(formatSignedDuration(0, make('fr').t).text).toBe('Aucun changement')
  })

  it('uses Polish one/few/many and English wording for recommendation counts', () => {
    const pl = make('pl').t
    const show = (n: number) => pl('optimize.readyHeading', { count: n }, n)
    expect(show(1)).toBe('1 rekomendacja gotowa do przeglądu')
    expect(show(3)).toBe('3 rekomendacje gotowe do przeglądu')
    expect(show(5)).toBe('5 rekomendacji gotowych do przeglądu')
    const en = make('en').t
    expect(en('optimize.readyHeading', { count: 1 }, 1)).toBe('1 recommendation ready for review')
    expect(en('optimize.readyHeading', { count: 2 }, 2)).toBe('2 recommendations ready for review')
  })

  it('builds target summaries from nested plurals', () => {
    const summary = (locale: 'en' | 'pl', count: number, trips: number) => {
      const t = make(locale).t
      return t('optimize.target.stopTimes', { count, trips: t('optimize.target.inTrips', { count: trips }, trips) }, count)
    }
    expect(summary('en', 3, 2)).toBe('3 stop times across 2 trips')
    expect(summary('pl', 5, 1)).toBe('5 czasów na przystankach w 1 kursie')
  })
})

describe('draft validation and compare labels', () => {
  it('pluralises validation counts per locale', () => {
    expect(make('pl').t('drafts.errors', { count: 2 }, 2)).toBe('2 błędy')
    expect(make('fr').t('drafts.errors', { count: 1 }, 1)).toBe('1 erreur')
    expect(make('en').t('drafts.errors', { count: 1 }, 1)).toBe('1 errors')
  })

  it('keeps English compare wording', () => {
    const t = make('en').t
    expect(t('compare.tripsChanged', { count: 1 }, 1)).toBe('1 trip changed')
    expect(t('compare.calendarsChanged', { count: 0 }, 0)).toBe('0 calendars changed')
    expect(t('compare.showingFirst', { shown: 200, count: 350 }, 350)).toBe('Showing first 200 of 350 changed trips.')
    expect(t('drafts.previewShift', { count: 4, delta: '+0:03' }, 4)).toBe('≈4 trips currently shown in the grid would shift by +0:03.')
    expect(make('pl').t('compare.tripsChanged', { count: 3 }, 3)).toBe('3 zmienione kursy')
  })
})

describe('compare and terminology fixes', () => {
  it('keeps the trailing period of the original English showing-first sentence', () => {
    const t = make('en').t
    expect(t('compare.showingFirst', { shown: 200, count: 1 }, 1)).toBe('Showing first 200 of 1 changed trips.')
    expect(t('compare.showingFirst', { shown: 3, count: 5 }, 5)).toBe('Showing first 3 of 5 changed trips.')
  })

  it('uses rewizja for revision in Polish', () => {
    const text = make('pl').t('explore.feed.activeRevision', { id: 4 })
    expect(text).toContain('rewizja')
    expect(text).not.toContain('wersja')
  })
})
