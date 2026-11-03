import type { UseTimeAgoMessages, UseTimeAgoUnitNamesDefault } from '@vueuse/core'

type TranslatePlural = (key: string, params?: Record<string, unknown>, plural?: number) => string

const SPECIAL: Record<'day' | 'week' | 'month' | 'year', { past: string, future: string }> = {
  day: { past: 'timeAgo.yesterday', future: 'timeAgo.tomorrow' },
  week: { past: 'timeAgo.lastWeek', future: 'timeAgo.nextWeek' },
  month: { past: 'timeAgo.lastMonth', future: 'timeAgo.nextMonth' },
  year: { past: 'timeAgo.lastYear', future: 'timeAgo.nextYear' },
}

/** vueuse `formatTimeAgo` messages built from i18n keys (English equals vueuse's defaults). */
// i18n-keys: timeAgo.*
export function timeAgoMessages(t: TranslatePlural): UseTimeAgoMessages<UseTimeAgoUnitNamesDefault> {
  const unit = (name: string) => (n: number) => t(`timeAgo.unit.${name}`, { count: n }, n)
  const special = (name: keyof typeof SPECIAL) => (n: number, past: boolean) =>
    n === 1 ? t(past ? SPECIAL[name].past : SPECIAL[name].future) : unit(name)(n)
  return {
    justNow: t('timeAgo.justNow'),
    past: (n) => (/\d/.test(String(n)) ? t('timeAgo.past', { time: n }) : String(n)),
    future: (n) => (/\d/.test(String(n)) ? t('timeAgo.future', { time: n }) : String(n)),
    invalid: '',
    second: unit('second'),
    minute: unit('minute'),
    hour: unit('hour'),
    day: special('day'),
    week: special('week'),
    month: special('month'),
    year: special('year'),
  }
}
