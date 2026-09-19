import {describe, expect, it} from 'vitest'
import {scheduledDepartureGroups, type ServingPattern} from '../../app/utils/stopSchedule'

describe('scheduledDepartureGroups', () => {
  it('uses each trip’s scheduled stop departure, filters the service day, and groups results by hour', () => {
    const serving = [
      {
        stopPathIndex: 2,
        offsetSec: 0,
        pattern: {
          patternKey: 'northbound',
          routeId: '5',
          headsign: 'North Terminal',
          route: {routeShortName: '5', routeColor: 'D92D2D', routeTextColor: 'FFFFFF'},
        },
      },
    ] as ServingPattern[]
    const trips = [
      {
        patternKey: 'northbound',
        trips: [
          {serviceId: 'weekday', startTimeSec: 6 * 3600, scheduleTimes: [{stopPathIndex: 2, departureSec: 6 * 3600 + 16 * 60}]},
          {serviceId: 'weekday', startTimeSec: 6 * 3600 + 30 * 60, scheduleTimes: [{stopPathIndex: 2, departureSec: 7 * 3600 + 4 * 60}]},
          {serviceId: 'saturday', startTimeSec: 6 * 3600, scheduleTimes: [{stopPathIndex: 2, departureSec: 6 * 3600 + 8 * 60}]},
        ],
      },
    ]
    const kinds = new Map([['weekday', new Set(['weekday' as const])], ['saturday', new Set(['saturday' as const])]])

    expect(scheduledDepartureGroups(serving, trips, kinds, 'weekday')).toEqual([
      {
        hour: 6,
        departures: [{routeLabel: '5', routeColor: 'D92D2D', routeTextColor: 'FFFFFF', headsign: 'North Terminal', departureSec: 6 * 3600 + 16 * 60}],
      },
      {
        hour: 7,
        departures: [{routeLabel: '5', routeColor: 'D92D2D', routeTextColor: 'FFFFFF', headsign: 'North Terminal', departureSec: 7 * 3600 + 4 * 60}],
      },
    ])
  })
})
