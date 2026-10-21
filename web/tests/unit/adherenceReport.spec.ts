import { describe, expect, it } from 'vitest'
import { isValidAdherenceRange } from '~/utils/adherenceReport'

describe('isValidAdherenceRange', () => {
  it('accepts a same-day range', () => {
    expect(isValidAdherenceRange('2026-01-05', '2026-01-05')).toBe(true)
  })
  it('accepts endDate after startDate', () => {
    expect(isValidAdherenceRange('2026-01-01', '2026-01-05')).toBe(true)
  })
  it('rejects endDate before startDate', () => {
    expect(isValidAdherenceRange('2026-01-05', '2026-01-01')).toBe(false)
  })
  it('rejects a malformed date', () => {
    expect(isValidAdherenceRange('2026-1-5', '2026-01-05')).toBe(false)
  })
})
