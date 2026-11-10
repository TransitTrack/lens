import {describe, expect, it} from 'vitest'
import {
  departureHistogram,
  routesOfCalls,
  scheduledDepartureGroups,
  serviceKinds,
  tripTotalsOf,
} from '../../app/utils/stopSchedule'
import type {StopSchedule, StopScheduleCall} from '../../app/api'

const call = (overrides: Partial<StopScheduleCall>): StopScheduleCall => ({
  patternKey: 'p',
  routeId: '5',
  tripCount: 3,
  stopPathIndex: 2,
  departures: [],
  ...overrides,
})

const schedule: StopSchedule = {
  calls: [
    call({
      patternKey: 'northbound',
      routeShortName: '5',
      routeColor: 'D92D2D',
      routeTextColor: 'FFFFFF',
      headsign: 'North Terminal',
      directionId: 0,
      tripCount: 10,
      departures: [
        {serviceId: 'weekday', departureSec: 6 * 3600 + 16 * 60},
        {serviceId: 'saturday', departureSec: 6 * 3600 + 8 * 60},
        {serviceId: 'weekday', departureSec: 7 * 3600 + 4 * 60},
      ],
    }),
    call({patternKey: 'southbound', routeId: '5', directionId: 1, tripCount: 4, headsign: 'South'}),
    call({patternKey: 'other', routeId: '9', routeShortName: null, tripCount: 2}),
  ],
  services: [
    {serviceId: 'weekday', weekday: true, saturday: false, sunday: false},
    {serviceId: 'saturday', weekday: false, saturday: true, sunday: false},
  ],
}
const kinds = serviceKinds(schedule.services)

describe('serviceKinds', () => {
  it('maps each service to the kinds of day it runs, and lists nothing for unknown services', () => {
    expect([...kinds.get('weekday')!]).toEqual(['weekday'])
    expect([...kinds.get('saturday')!]).toEqual(['saturday'])
    expect(kinds.has('holiday')).toBe(false)
  })
})

describe('scheduledDepartureGroups', () => {
  it('filters to the service day, labels each departure with its pattern, and groups by hour', () => {
    expect(scheduledDepartureGroups(schedule, kinds, 'weekday')).toEqual([
      {
        hour: 6,
        departures: [{routeLabel: '5', routeColor: 'D92D2D', routeTextColor: 'FFFFFF', headsign: 'North Terminal', departureSec: 6 * 3600 + 16 * 60}],
      },
      {
        hour: 7,
        departures: [{routeLabel: '5', routeColor: 'D92D2D', routeTextColor: 'FFFFFF', headsign: 'North Terminal', departureSec: 7 * 3600 + 4 * 60}],
      },
    ])
    expect(scheduledDepartureGroups(schedule, kinds, 'sunday')).toEqual([])
  })

  it('orders departures from different patterns together by time', () => {
    const merged: StopSchedule = {
      calls: [
        call({routeShortName: 'A', departures: [{serviceId: 'weekday', departureSec: 7200}]}),
        call({routeShortName: 'B', departures: [{serviceId: 'weekday', departureSec: 3600}, {serviceId: 'weekday', departureSec: 7300}]}),
      ],
      services: schedule.services,
    }
    const groups = scheduledDepartureGroups(merged, kinds, 'weekday')
    expect(groups.map((g) => [g.hour, g.departures.map((d) => d.routeLabel)])).toEqual([
      [1, ['B']],
      [2, ['A', 'B']],
    ])
  })
})

describe('departureHistogram', () => {
  const t = (key: string, params?: Record<string, unknown>, plural?: number) => `${key}:${params?.from}-${params?.to}:${plural}`

  it('counts departures per hour of the chosen service day, with the first and last of them', () => {
    const histogram = departureHistogram(schedule, kinds, 'weekday', t)
    expect(histogram.total).toBe(2)
    expect(histogram.firstSec).toBe(6 * 3600 + 16 * 60)
    expect(histogram.lastSec).toBe(7 * 3600 + 4 * 60)
    expect(histogram.bars).toHaveLength(24)
    expect(histogram.bars[6]!.value).toBe(1)
    expect(histogram.bars[7]!.value).toBe(1)
    expect(histogram.bars[8]!.value).toBe(0)
    expect(histogram.bars[6]!.hint).toBe('explore.stop.hourHint:6-7:1')
  })

  it('wraps service-day times past midnight into the 24 bars and reports no span when nothing runs', () => {
    const late: StopSchedule = {
      calls: [call({departures: [{serviceId: 'weekday', departureSec: 25 * 3600}]})],
      services: schedule.services,
    }
    expect(departureHistogram(late, kinds, 'weekday', t).bars[1]!.value).toBe(1)
    const none = departureHistogram(schedule, kinds, 'sunday', t)
    expect(none.total).toBe(0)
    expect(none.firstSec).toBeNull()
    expect(none.lastSec).toBeNull()
  })
})

describe('routesOfCalls and tripTotalsOf', () => {
  it('lists each route once, falling back to the id when it has no short name', () => {
    expect(routesOfCalls(schedule.calls)).toEqual([
      {id: '5', name: '5', color: 'D92D2D'},
      {id: '9', name: '9', color: null},
    ])
  })

  it('sums trips across the calling patterns and per direction', () => {
    expect(tripTotalsOf(schedule.calls)).toEqual({total: 16, dir0: 10, dir1: 4})
  })
})
