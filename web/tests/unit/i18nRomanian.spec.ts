import { describe, expect, it } from 'vitest'
import { createI18n } from 'vue-i18n'
import { pluralRules } from '../../i18n/pluralRules'
import en from '../../i18n/locales/en.json'
import ro from '../../i18n/locales/ro.json'

const { t } = createI18n({ legacy: false, locale: 'ro', fallbackLocale: 'en', messages: { en, ro }, pluralRules }).global

describe('ro plural rule', () => {
  const rule = pluralRules.ro!
  it('maps counts to one | few | other', () => {
    const expected: Record<number, number> = {
      0: 1, 1: 0, 2: 1, 19: 1, 20: 2, 21: 2, 100: 2, 101: 2, 102: 1, 119: 1, 120: 2,
    }
    for (const [n, idx] of Object.entries(expected)) expect(rule(Number(n), 3), `n=${n}`).toBe(idx)
  })
  it('falls back to one | other for 2-form messages', () => {
    expect(rule(1, 2)).toBe(0)
    expect(rule(5, 2)).toBe(1)
    expect(rule(0, 2)).toBe(1)
  })
})

describe('ro plural messages', () => {
  const vehicles = (n: number) => t('vehicles.detail.stopsCount', { count: n }, n)
  it('uses the "de" preposition from 20 up', () => {
    expect(vehicles(1)).toBe('1 stație')
    expect(vehicles(2)).toBe('2 stații')
    expect(vehicles(19)).toBe('19 stații')
    expect(vehicles(20)).toBe('20 de stații')
    expect(vehicles(22)).toBe('22 de stații')
    expect(vehicles(101)).toBe('101 de stații')
    expect(vehicles(102)).toBe('102 stații')
  })
  it('pluralises time units', () => {
    const minutes = (n: number) => t('timeAgo.unit.minute', { count: n }, n)
    expect(minutes(1)).toBe('1 minut')
    expect(minutes(2)).toBe('2 minute')
    expect(minutes(20)).toBe('20 de minute')
  })
})
