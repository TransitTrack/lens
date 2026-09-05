import { describe, expect, it } from 'vitest'
import { adherenceBadge } from '../../app/utils/adherence'

describe('adherenceBadge', () => {
  it('null/undefined -> gray Unscheduled', () => {
    expect(adherenceBadge(null)).toEqual({ label: 'Unscheduled', color: 'gray' })
    expect(adherenceBadge(undefined)).toEqual({ label: 'Unscheduled', color: 'gray' })
  })

  it('> 300s late -> red', () => {
    const b = adherenceBadge(360)
    expect(b.color).toBe('red')
    expect(b.label).toContain('late')
  })

  it('boundary at exactly 300s is NOT late (green)', () => {
    expect(adherenceBadge(300).color).toBe('green')
  })

  it('< -60s early -> amber', () => {
    const b = adherenceBadge(-120)
    expect(b.color).toBe('amber')
    expect(b.label).toContain('early')
  })

  it('boundary at exactly -60s is NOT early (green)', () => {
    expect(adherenceBadge(-60).color).toBe('green')
  })

  it('within the on-time band -> green', () => {
    expect(adherenceBadge(0).color).toBe('green')
    expect(adherenceBadge(-30).color).toBe('green')
    expect(adherenceBadge(200).color).toBe('green')
  })
})
