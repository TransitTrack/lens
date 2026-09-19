import {describe, expect, it} from 'vitest'
import {ganttRows, layoverLabel, timelineBarHeight, timelineTicks, type TimelineSegment} from '../../app/utils/blocks'

describe('timelineTicks', () => {
  it('includes the duty endpoints and each intervening hour', () => {
    expect(timelineTicks(6 * 3600 + 15 * 60, 9 * 3600 + 10 * 60)).toEqual([
      {sec: 6 * 3600 + 15 * 60, label: '06:15', endpoint: true},
      {sec: 7 * 3600, label: '07:00', endpoint: false},
      {sec: 8 * 3600, label: '08:00', endpoint: false},
      {sec: 9 * 3600, label: '09:00', endpoint: false},
      {sec: 9 * 3600 + 10 * 60, label: '09:10', endpoint: true},
    ])
  })

  it('does not duplicate an endpoint that falls exactly on an hour', () => {
    expect(timelineTicks(7 * 3600, 9 * 3600)).toEqual([
      {sec: 7 * 3600, label: '07:00', endpoint: true},
      {sec: 8 * 3600, label: '08:00', endpoint: false},
      {sec: 9 * 3600, label: '09:00', endpoint: true},
    ])
  })
})

describe('timelineBarHeight', () => {
  it('keeps trip bars compact when the timeline grows taller', () => {
    expect(timelineBarHeight(132)).toBe(34)
    expect(timelineBarHeight(200)).toBe(34)
  })
})

describe('ganttRows', () => {
  it('creates one row per trip and attaches its following layover', () => {
    const segments: TimelineSegment[] = [
      {key: 't-0', kind: 'trip', x: 0, w: 0.2, color: '#123456', routeLabel: '1', headsign: 'Centre', startSec: 0, endSec: 600, deadheadAfter: false},
      {key: 'l-0', kind: 'layover', x: 0.2, w: 0.1, color: '#64748b', routeLabel: '', headsign: null, startSec: 600, endSec: 900, deadheadAfter: false},
      {key: 't-1', kind: 'trip', x: 0.3, w: 0.2, color: '#654321', routeLabel: '2', headsign: 'Station', startSec: 900, endSec: 1500, deadheadAfter: true},
    ]

    expect(ganttRows(segments)).toEqual([
      {trip: segments[0], layoverAfter: segments[1]},
      {trip: segments[2], layoverAfter: null},
    ])
  })
})

describe('layoverLabel', () => {
  it('formats the duration and calls out a following deadhead', () => {
    expect(layoverLabel(600, false)).toBe('10 min layover')
    expect(layoverLabel(600, true)).toBe('10 min layover · deadhead follows')
  })
})
