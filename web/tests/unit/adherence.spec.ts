import { describe, expect, it } from 'vitest'
import { adherenceBadge } from '../../app/utils/adherence'

describe('adherenceBadge', () => {
  it('null/undefined -> neutral Unscheduled', () => {
    expect(adherenceBadge(null)).toEqual({ label: 'Unscheduled', color: 'neutral' })
    expect(adherenceBadge(undefined)).toEqual({ label: 'Unscheduled', color: 'neutral' })
  })

  it('> 300s late -> error', () => {
    const b = adherenceBadge(360)
    expect(b.color).toBe('error')
    expect(b.label).toContain('late')
  })

  it('boundary at exactly 300s is NOT late (success)', () => {
    expect(adherenceBadge(300).color).toBe('success')
  })

  it('< -60s early -> warning', () => {
    const b = adherenceBadge(-120)
    expect(b.color).toBe('warning')
    expect(b.label).toContain('early')
  })

  it('boundary at exactly -60s is NOT early (success)', () => {
    expect(adherenceBadge(-60).color).toBe('success')
  })

  it('within the on-time band -> success', () => {
    expect(adherenceBadge(0).color).toBe('success')
    expect(adherenceBadge(-30).color).toBe('success')
    expect(adherenceBadge(200).color).toBe('success')
  })
})
