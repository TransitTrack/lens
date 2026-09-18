import {describe, expect, it} from 'vitest'
import {formatOptimizationKind, formatSignedDuration} from '../../app/utils/optimizationPresentation'

describe('optimization presentation', () => {
  it('turns analyzer kinds into planner-friendly labels', () => {
    expect(formatOptimizationKind('TRIP_SHIFT')).toBe('Trip shift')
    expect(formatOptimizationKind('STOP_TIME')).toBe('Stop timing')
  })

  it('formats a signed duration with a direction cue', () => {
    expect(formatSignedDuration(75)).toEqual({text: '+1m 15s', direction: 'later'})
    expect(formatSignedDuration(-45)).toEqual({text: '−45s', direction: 'earlier'})
    expect(formatSignedDuration(0)).toEqual({text: 'No change', direction: 'unchanged'})
  })
})
