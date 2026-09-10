import { describe, expect, it } from 'vitest'
import { formatHm, parseHm, inWindow, TIME_PRESETS } from '../../app/utils/tripFilters'

describe('formatHm / parseHm', () => {
  it('formats seconds of day, keeping the 24h+ clock', () => {
    expect(formatHm(6 * 3600 + 5 * 60)).toBe('06:05')
    expect(formatHm(25 * 3600 + 30 * 60)).toBe('25:30')
    expect(formatHm(null)).toBe('—')
  })

  it('parses H:MM and HH:MM', () => {
    expect(parseHm('6:05')).toBe(6 * 3600 + 5 * 60)
    expect(parseHm('25:30')).toBe(25 * 3600 + 30 * 60)
  })

  it('rejects garbage', () => {
    expect(parseHm('')).toBeNull()
    expect(parseHm('6h')).toBeNull()
    expect(parseHm('6:99')).toBeNull()
    expect(parseHm(null)).toBeNull()
  })

  it('round-trips', () => {
    expect(formatHm(parseHm('14:20')!)).toBe('14:20')
  })
})

describe('inWindow', () => {
  const start = 8 * 3600 // 08:00

  it('open on both sides matches anything', () => {
    expect(inWindow(start, null, null)).toBe(true)
    expect(inWindow(null, null, null)).toBe(true)
  })

  it('respects an inclusive lower and exclusive upper bound', () => {
    expect(inWindow(start, 6 * 3600, 10 * 3600)).toBe(true)
    expect(inWindow(6 * 3600, 6 * 3600, 10 * 3600)).toBe(true) // lower inclusive
    expect(inWindow(10 * 3600, 6 * 3600, 10 * 3600)).toBe(false) // upper exclusive
    expect(inWindow(start, 9 * 3600, null)).toBe(false)
  })

  it('null start never matches a bounded window', () => {
    expect(inWindow(null, 6 * 3600, 10 * 3600)).toBe(false)
  })
})

describe('TIME_PRESETS', () => {
  it('AM peak is 06:00–10:00, late is 24h open-ended', () => {
    const am = TIME_PRESETS.find((p) => p.key === 'am')!
    expect([am.fromSec, am.toSec]).toEqual([6 * 3600, 10 * 3600])
    const late = TIME_PRESETS.find((p) => p.key === 'late')!
    expect([late.fromSec, late.toSec]).toEqual([24 * 3600, null])
  })
})
