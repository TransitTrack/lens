import { describe, expect, it } from 'vitest'
import { formatClock } from '../../app/utils/clock'

describe('formatClock', () => {
  const evening = '2026-10-25T16:40:09Z' // 18:40:09 in Bucharest (EET, +2)

  it('uses a 24-hour clock, never AM/PM', () => {
    expect(formatClock(evening, { timeZone: 'Europe/Bucharest' })).toBe('18:40')
    expect(formatClock('2026-10-25T04:25:00Z', { timeZone: 'Europe/Bucharest' })).toBe('06:25')
  })

  it('adds seconds on request', () => {
    expect(formatClock(evening, { seconds: true, timeZone: 'Europe/Bucharest' })).toBe('18:40:09')
  })

  it('shows midnight as 00, not 24', () => {
    expect(formatClock('2026-10-25T22:05:00Z', { timeZone: 'Europe/Bucharest' })).toBe('00:05')
  })

  it('passes through what it cannot read', () => {
    expect(formatClock(null)).toBe('—')
    expect(formatClock('not a time')).toBe('not a time')
  })
})
