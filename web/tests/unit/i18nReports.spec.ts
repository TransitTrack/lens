import { describe, expect, it } from 'vitest'
import { createI18n } from 'vue-i18n'
import { pluralRules } from '../../i18n/pluralRules'
import en from '../../i18n/locales/en.json'
import pl from '../../i18n/locales/pl.json'
import fr from '../../i18n/locales/fr.json'
import { adherenceBadge, formatAdherence } from '../../app/utils/adherence'
import { assignmentLabel } from '../../app/utils/avlReport'

function make(locale: 'en' | 'pl' | 'fr') {
  return createI18n({
    legacy: false,
    locale,
    fallbackLocale: 'en',
    messages: { en, pl, fr },
    pluralRules,
  }).global
}

describe('adherence labels', () => {
  it('keeps English wording and colours', () => {
    const t = make('en').t
    expect(adherenceBadge(null, t)).toEqual({ label: 'Unscheduled', color: 'neutral' })
    expect(adherenceBadge(360, t)).toEqual({ label: '6m late', color: 'error' })
    expect(adherenceBadge(-120, t)).toEqual({ label: '2m early', color: 'warning' })
    expect(adherenceBadge(0, t)).toEqual({ label: 'On time', color: 'success' })
    expect(formatAdherence(0, t)).toBe('On time')
    expect(formatAdherence(125, t)).toBe('+02:05')
  })

  it('translates labels to Polish and French without changing colours', () => {
    const pl = make('pl').t
    expect(adherenceBadge(360, pl)).toEqual({ label: '6 min opóźnienia', color: 'error' })
    expect(adherenceBadge(0, pl).label).toBe('Punktualnie')
    expect(formatAdherence(undefined, pl)).toBe('Poza rozkładem')
    expect(adherenceBadge(-120, make('fr').t)).toEqual({ label: "2 min d'avance", color: 'warning' })
  })

  it('translates the assignment fallback', () => {
    expect(assignmentLabel({}, make('en').t)).toBe('n/a')
    expect(assignmentLabel({}, make('pl').t)).toBe('b/d')
    expect(assignmentLabel({ descTripId: 'T1' }, make('pl').t)).toBe('T1')
  })
})

describe('report plurals', () => {
  it('uses Polish one/few/many for the AVL report count', () => {
    const t = make('pl').t
    const show = (n: number) => t('reports.pagination.showing', { start: 1, end: n, count: n }, n)
    expect(show(1)).toBe('Pokazano 1–1 z 1 raportu')
    expect(show(3)).toBe('Pokazano 1–3 z 3 raportów')
    expect(show(5)).toBe('Pokazano 1–5 z 5 raportów')
  })

  it('keeps English recent gap wording', () => {
    const t = make('en').t
    expect(t('reports.headway.recentGaps', { count: 1 }, 1)).toBe('1 recent gap sampled')
    expect(t('reports.headway.recentGaps', { count: 0 }, 0)).toBe('0 recent gaps sampled')
    expect(t('reports.headway.recentGaps', { count: 12 }, 12)).toBe('12 recent gaps sampled')
    expect(make('pl').t('reports.headway.recentGaps', { count: 3 }, 3)).toBe('Próbka: 3 ostatnie przerwy')
  })
})

describe('noRealtime.body agreement', () => {
  const whats = ['noRealtime.liveVehicleData', 'noRealtime.liveVehicleOperations', 'noRealtime.liveNetwork', 'vehicles.noRealtimeWhat', 'reports.noRealtime.adherence', 'reports.noRealtime.avl', 'reports.noRealtime.headway']
  const render = (loc: 'en' | 'pl' | 'fr', key: string) => {
    const i = make(loc)
    return i.t('noRealtime.body', { name: 'X', what: i.t(key) })
  }

  it('keeps the English sentence', () => {
    expect(render('en', 'noRealtime.liveNetwork')).toBe(
      "X has no linked AVL feed, so the live network isn't available. Explore the static GTFS data instead.",
    )
  })

  it('uses a verb-neutral structure in pl and fr for every caller phrase', () => {
    for (const key of whats) {
      const p = make('pl').t(key)
      const f = make('fr').t(key)
      expect(render('pl', key)).toBe(
        `X nie ma powiązanego źródła AVL, więc niedostępne: ${p}. Zamiast tego przeglądaj statyczne dane GTFS.`,
      )
      expect(render('fr', key)).toBe(
        `X n'a pas de flux AVL associé, donc indisponible : ${f}. Explorez plutôt les données GTFS statiques.`,
      )
    }
  })
})
