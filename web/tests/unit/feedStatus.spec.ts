import { describe, expect, it } from 'vitest'
import {
  revisionStatusMeta,
  cronSummary,
  rowCount,
  PIPELINE_STEPS,
} from '../../app/utils/feedStatus'

describe('revisionStatusMeta', () => {
  it('maps pipeline stages to an ordered step', () => {
    expect(revisionStatusMeta('VALIDATING')).toMatchObject({
      label: 'Validating',
      step: 1,
      terminal: false,
    })
    expect(revisionStatusMeta('ACTIVE')).toMatchObject({
      label: 'Active',
      color: 'success',
      terminal: true,
    })
    expect(revisionStatusMeta('ACTIVE').step).toBe(PIPELINE_STEPS.length - 1)
  })

  it('failed is error, step -1, terminal', () => {
    expect(revisionStatusMeta('FAILED')).toEqual({
      label: 'Failed',
      color: 'error',
      step: -1,
      terminal: true,
    })
  })

  it('unknown / null falls back to neutral', () => {
    expect(revisionStatusMeta(null).color).toBe('neutral')
    expect(revisionStatusMeta('WHAT').label).toBe('WHAT')
  })
})

describe('cronSummary', () => {
  it('recognises common Spring 6-field patterns', () => {
    expect(cronSummary('0 */15 * * * *')).toBe('every 15 min')
    expect(cronSummary('0 0 */6 * * *')).toBe('every 6 h')
    expect(cronSummary('0 30 3 * * *')).toBe('daily at 03:30')
  })

  it('falls back to the raw string / a note', () => {
    expect(cronSummary('0 0 12 * * MON-FRI')).toBe('0 0 12 * * MON-FRI')
    expect(cronSummary(null)).toBe('not scheduled')
    expect(cronSummary('')).toBe('not scheduled')
  })
})

describe('rowCount', () => {
  it('reads a gtfs_<entity> key', () => {
    expect(rowCount({ gtfs_route: 59, gtfs_stop: 896 }, 'route')).toBe(59)
    expect(rowCount({ gtfs_route: 59 }, 'trip')).toBeNull()
    expect(rowCount(null, 'route')).toBeNull()
  })
})
