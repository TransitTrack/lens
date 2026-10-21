import { describe, expect, it } from 'vitest'
import {
  assignmentLabel,
  formatReportSource,
  formatReportTimestamp,
  isValidReportRange,
} from '../../app/utils/avlReport'

describe('isValidReportRange', () => {
  it('requires a same-day end time after the start time', () => {
    expect(isValidReportRange('2026-09-07', '08:10', '08:12')).toBe(true)
    expect(isValidReportRange('2026-09-07', '08:12', '08:12')).toBe(false)
    expect(isValidReportRange('2026-09-07', '08:12', '08:10')).toBe(false)
  })
})

describe('assignmentLabel', () => {
  it('prefers the matched trip, then the source descriptor, then n/a', () => {
    expect(assignmentLabel({ match: { trip: { tripId: 'T-42' } }, descTripId: 'raw-trip' })).toBe('T-42')
    expect(assignmentLabel({ match: null, descTripId: 'raw-trip' })).toBe('raw-trip')
    expect(assignmentLabel({ match: null, descTripId: null })).toBe('n/a')
  })
})

describe('report display formatting', () => {
  it('uses the agency time zone and readable source label', () => {
    expect(formatReportTimestamp('2026-09-07T06:10:00Z', 'Europe/Warsaw')).toBe('07/09/2026, 08:10:00')
    expect(formatReportSource('GTFS_RT')).toBe('GTFS-RT')
  })
})
