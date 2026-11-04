import { describe, expect, it } from 'vitest'
import { createI18n } from 'vue-i18n'
import en from '../../i18n/locales/en.json'
import { arrivalRange, arrivalRangeLabel, lateBadge } from '../../app/utils/predictionRange'

const t = createI18n({ legacy: false, locale: 'en', messages: { en } }).global.t

const NOW = Date.parse('2026-11-04T10:00:00Z')
const at = (offsetSec: number) => new Date(NOW + offsetSec * 1000).toISOString()

describe('arrivalRangeLabel', () => {
  it('shows the ETA and its range in minutes', () => {
    const p = { predictedArrival: at(240), rangeLowSec: -60, rangeHighSec: 120 }
    expect(arrivalRangeLabel(p, NOW, t)).toBe('4 min, likely 3 to 6')
  })

  it('shows only the ETA when the range has zero width', () => {
    const p = { predictedArrival: at(240), rangeLowSec: 0, rangeHighSec: 0 }
    expect(arrivalRangeLabel(p, NOW, t)).toBe('4 min')
  })

  it('shows only the ETA when the range collapses after rounding to minutes', () => {
    const p = { predictedArrival: at(600), rangeLowSec: -20, rangeHighSec: 20 }
    expect(arrivalRangeLabel(p, NOW, t)).toBe('10 min')
  })

  it('rounds the ETA and both range ends to the nearest minute', () => {
    // ETA 270 s -> 5 min; low 220 s -> 4 min; high 370 s -> 6 min.
    const p = { predictedArrival: at(270), rangeLowSec: -50, rangeHighSec: 100 }
    expect(arrivalRangeLabel(p, NOW, t)).toBe('5 min, likely 4 to 6')
    expect(arrivalRange(p, NOW)).toEqual({ unit: 'min', eta: 5, low: 4, high: 6 })
  })

  it('never puts the low end before now', () => {
    const p = { predictedArrival: at(150), rangeLowSec: -300, rangeHighSec: 90 }
    expect(arrivalRangeLabel(p, NOW, t)).toBe('3 min, likely 0 to 4')
  })

  it('returns null without a range or a predicted arrival', () => {
    expect(
      arrivalRangeLabel(
        { predictedArrival: at(240), rangeLowSec: null, rangeHighSec: null },
        NOW,
        t,
      ),
    ).toBeNull()
    expect(
      arrivalRangeLabel(
        { predictedArrival: at(240), rangeLowSec: -60, rangeHighSec: null },
        NOW,
        t,
      ),
    ).toBeNull()
    expect(
      arrivalRangeLabel({ predictedArrival: at(240), rangeLowSec: null, rangeHighSec: 60 }, NOW, t),
    ).toBeNull()
    expect(
      arrivalRangeLabel({ predictedArrival: null, rangeLowSec: -60, rangeHighSec: 60 }, NOW, t),
    ).toBeNull()
    expect(
      arrivalRangeLabel(
        { predictedArrival: 'not a date', rangeLowSec: -60, rangeHighSec: 60 },
        NOW,
        t,
      ),
    ).toBeNull()
  })

  it('uses seconds when the whole range ends within two minutes', () => {
    const p = { predictedArrival: at(40), rangeLowSec: -20, rangeHighSec: 30 }
    expect(arrivalRangeLabel(p, NOW, t)).toBe('40 s, likely 20 to 70')
    expect(arrivalRange(p, NOW)).toEqual({ unit: 'sec', eta: 40, low: 20, high: 70 })
  })

  it('shows only the ETA in seconds when a short range has zero width', () => {
    const p = { predictedArrival: at(25), rangeLowSec: 0, rangeHighSec: 0 }
    expect(arrivalRangeLabel(p, NOW, t)).toBe('25 s')
  })

  it('returns null once the whole range lies in the past', () => {
    const p = { predictedArrival: at(-200), rangeLowSec: -60, rangeHighSec: 60 }
    expect(arrivalRangeLabel(p, NOW, t)).toBeNull()
  })
})

describe('lateBadge', () => {
  it('marks a late assessment with its probability', () => {
    expect(lateBadge({ late: true, lateProbability: 0.724 }, t)).toEqual({
      color: 'error',
      label: 'Late',
      probability: '72% chance of being late',
    })
  })

  it('marks an on-time assessment with its probability', () => {
    expect(lateBadge({ late: false, lateProbability: 0.15 }, t)).toEqual({
      color: 'success',
      label: 'On time',
      probability: '15% chance of being late',
    })
  })

  it('has no probability text without a probability', () => {
    expect(lateBadge({ late: true, lateProbability: null }, t)).toEqual({
      color: 'error',
      label: 'Late',
      probability: null,
    })
  })

  it('returns null when there is no assessment', () => {
    expect(lateBadge({ late: null, lateProbability: 0.4 }, t)).toBeNull()
    expect(lateBadge(null, t)).toBeNull()
  })
})
