import {describe, expect, it} from 'vitest'
import {createI18n} from 'vue-i18n'
import {pluralRules} from '../../i18n/pluralRules'
import en from '../../i18n/locales/en.json'
import {formatOptimizationKind, formatSignedDuration} from '../../app/utils/optimizationPresentation'

const {t} = createI18n({legacy: false, locale: 'en', messages: {en}, pluralRules}).global

describe('optimization presentation', () => {
  it('turns analyzer kinds into planner-friendly labels', () => {
    expect(formatOptimizationKind('TRIP_SHIFT', t)).toBe('Trip shift')
    expect(formatOptimizationKind('STOP_TIME', t)).toBe('Stop timing')
    expect(formatOptimizationKind('SOMETHING_NEW', t)).toBe('SOMETHING_NEW')
  })

  it('formats a signed duration with a direction cue', () => {
    expect(formatSignedDuration(75, t)).toEqual({text: '+1m 15s', direction: 'later'})
    expect(formatSignedDuration(-45, t)).toEqual({text: '−45s', direction: 'earlier'})
    expect(formatSignedDuration(120, t)).toEqual({text: '+2m', direction: 'later'})
    expect(formatSignedDuration(0, t)).toEqual({text: 'No change', direction: 'unchanged'})
  })
})
