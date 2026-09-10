import { describe, expect, it } from 'vitest'
import { formatSpeed, formatDistance } from '../../app/utils/units'

describe('formatSpeed', () => {
  it('metric → km/h', () => {
    expect(formatSpeed(10, 'metric')).toBe('36 km/h')
    expect(formatSpeed(0, 'metric')).toBe('0 km/h')
  })

  it('imperial → mph', () => {
    expect(formatSpeed(10, 'imperial')).toBe('22 mph')
  })

  it('null / non-finite → dash', () => {
    expect(formatSpeed(null, 'metric')).toBe('—')
    expect(formatSpeed(undefined, 'imperial')).toBe('—')
    expect(formatSpeed(Number.NaN, 'metric')).toBe('—')
  })
})

describe('formatDistance', () => {
  it('metric: m under 1 km, km above', () => {
    expect(formatDistance(150, 'metric')).toBe('150 m')
    expect(formatDistance(999, 'metric')).toBe('999 m')
    expect(formatDistance(1500, 'metric')).toBe('1.5 km')
  })

  it('imperial: ft under a tenth of a mile, mi above', () => {
    expect(formatDistance(100, 'imperial')).toBe('328 ft')
    expect(formatDistance(3218.7, 'imperial')).toBe('2.0 mi')
  })

  it('null / non-finite → dash', () => {
    expect(formatDistance(null, 'metric')).toBe('—')
    expect(formatDistance(Number.NaN, 'imperial')).toBe('—')
  })
})
