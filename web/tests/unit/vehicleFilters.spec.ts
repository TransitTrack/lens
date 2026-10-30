import { describe, expect, it } from 'vitest'
import type { VehicleRow } from '../../app/composables/useVehiclePolling'
import { fixtures } from '../../mocks/handlers'
import {
  emptyFilters,
  filterVehicles,
  routeFacets,
  sortVehicles,
  stateCounts,
  vehicleState,
} from '../../app/utils/vehicleFilters'

function vehicle(overrides: Partial<VehicleRow> & { routeId?: string } = {}): VehicleRow {
  const { routeId = '501', ...rest } = overrides
  return {
    vehicleId: 'v',
    label: null,
    reportTs: '2026-10-29T20:00:00Z',
    position: { lat: 52.2, lon: 17.0 },
    bearing: null,
    matched: true,
    stale: false,
    scheduleAdherenceSec: 0,
    stopPathIndex: 5,
    distanceAlongTripM: 5_000,
    speedMps: 8,
    pattern: { lengthM: 20_000 },
    trip: {
      tripId: `trip-${routeId}`,
      routeId,
      tripHeadsign: 'Kórnik Bnin',
      route: { routeShortName: routeId, routeColor: null, routeTextColor: null },
    },
    block: { blockId: `block-${routeId}`, serviceId: 'WK' },
    ...rest,
  }
}

const fixtureVehicles = fixtures.vehicles as unknown as VehicleRow[]

describe('vehicleState', () => {
  it('splits matched, reporting vehicles by schedule adherence', () => {
    expect(vehicleState(vehicle({ scheduleAdherenceSec: 0 }))).toBe('ontime')
    expect(vehicleState(vehicle({ scheduleAdherenceSec: 60 }))).toBe('ontime')
    expect(vehicleState(vehicle({ scheduleAdherenceSec: 61 }))).toBe('late')
    expect(vehicleState(vehicle({ scheduleAdherenceSec: -61 }))).toBe('early')
    expect(vehicleState(vehicle({ scheduleAdherenceSec: null }))).toBe('ontime')
  })

  it('puts a vehicle standing at either end of its trip at the terminus, whatever its delay', () => {
    const standing = { speedMps: 0, scheduleAdherenceSec: 1_763 }
    expect(vehicleState(vehicle({ ...standing, stopPathIndex: 0, distanceAlongTripM: 0 }))).toBe(
      'terminus',
    )
    expect(
      vehicleState(vehicle({ ...standing, stopPathIndex: 18, distanceAlongTripM: 19_980 })),
    ).toBe('terminus')
    // moving through the last stretch, or standing mid-trip, isn't a terminus
    expect(vehicleState(vehicle({ ...standing, speedMps: 6, distanceAlongTripM: 19_980 }))).toBe(
      'late',
    )
    expect(vehicleState(vehicle({ ...standing, distanceAlongTripM: 9_000 }))).toBe('late')
  })

  it('reports lost signal before anything schedule-related, and unmatched first of all', () => {
    expect(vehicleState(vehicle({ stale: true, scheduleAdherenceSec: 600 }))).toBe('stale')
    expect(vehicleState(vehicle({ matched: false, stale: true }))).toBe('unmatched')
  })
})

describe('filterVehicles', () => {
  it('matches vehicle labels and identifiers without case sensitivity', () => {
    expect(filterVehicles(fixtureVehicles, { ...emptyFilters(), query: '101' })).toEqual([
      fixtureVehicles[0],
    ])
    expect(filterVehicles(fixtureVehicles, { ...emptyFilters(), query: 'BUS-2' })).toEqual([
      fixtureVehicles[1],
    ])
  })

  it('finds vehicles by trip and block id', () => {
    const a = vehicle({ vehicleId: 'a', routeId: '501' })
    const b = vehicle({ vehicleId: 'b', routeId: '560' })
    expect(filterVehicles([a, b], { ...emptyFilters(), query: 'trip-560' })).toEqual([b])
    expect(filterVehicles([a, b], { ...emptyFilters(), query: 'BLOCK-501' })).toEqual([a])
  })

  it('filters by one state and any of several routes', () => {
    const late501 = vehicle({ vehicleId: 'a', routeId: '501', scheduleAdherenceSec: 300 })
    const ok501 = vehicle({ vehicleId: 'b', routeId: '501' })
    const late560 = vehicle({ vehicleId: 'c', routeId: '560', scheduleAdherenceSec: 300 })
    const late527 = vehicle({ vehicleId: 'd', routeId: '527', scheduleAdherenceSec: 300 })
    const all = [late501, ok501, late560, late527]
    expect(filterVehicles(all, { ...emptyFilters(), state: 'late' })).toEqual([
      late501,
      late560,
      late527,
    ])
    expect(
      filterVehicles(all, { ...emptyFilters(), state: 'late', routeIds: ['501', '560'] }),
    ).toEqual([late501, late560])
  })

  it("doesn't put unmatched vehicles on the route their stale trip data names", () => {
    const parked = vehicle({ vehicleId: 'p', matched: false, routeId: '501' })
    expect(filterVehicles([parked], { ...emptyFilters(), routeIds: ['501'] })).toEqual([])
    expect(filterVehicles([parked], { ...emptyFilters(), query: 'trip-501' })).toEqual([])
  })
})

describe('stateCounts', () => {
  it('counts every state plus all, honouring search and routes but not the chosen state', () => {
    const all = [
      vehicle({ vehicleId: 'a', routeId: '501', scheduleAdherenceSec: 300 }),
      vehicle({ vehicleId: 'b', routeId: '501' }),
      vehicle({ vehicleId: 'c', routeId: '560', stale: true }),
      vehicle({ vehicleId: 'd', matched: false }),
    ]
    expect(stateCounts(all, { ...emptyFilters(), state: 'late' })).toEqual({
      all: 4,
      ontime: 1,
      late: 1,
      early: 0,
      terminus: 0,
      stale: 1,
      unmatched: 1,
    })
    expect(stateCounts(all, { ...emptyFilters(), routeIds: ['501'] })).toMatchObject({
      all: 2,
      late: 1,
      ontime: 1,
    })
  })
})

describe('routeFacets', () => {
  it('lists routes with vehicles, counted for the chosen state, busiest first', () => {
    const all = [
      vehicle({ vehicleId: 'a', routeId: '501', scheduleAdherenceSec: 300 }),
      vehicle({ vehicleId: 'b', routeId: '501' }),
      vehicle({ vehicleId: 'c', routeId: '560', scheduleAdherenceSec: 300 }),
      vehicle({ vehicleId: 'd', routeId: '560', scheduleAdherenceSec: 300 }),
      vehicle({ vehicleId: 'e', routeId: '527' }),
      vehicle({ vehicleId: 'f', matched: false, routeId: '999' }),
    ]
    expect(routeFacets(all, emptyFilters()).map((r) => [r.routeId, r.count])).toEqual([
      ['501', 2],
      ['560', 2],
      ['527', 1],
    ])
    expect(
      routeFacets(all, { ...emptyFilters(), state: 'late' }).map((r) => [r.routeId, r.count]),
    ).toEqual([
      ['560', 2],
      ['501', 1],
      ['527', 0],
    ])
  })

  it('keeps a selected route listed after its last vehicle leaves, so it can be unticked', () => {
    expect(
      routeFacets([vehicle({ routeId: '501' })], { ...emptyFilters(), routeIds: ['777'] }),
    ).toContainEqual(expect.objectContaining({ routeId: '777', count: 0 }))
  })
})

describe('sortVehicles', () => {
  const v = (vehicleId: string, matched: boolean, label: string | null = null) =>
    vehicle({ vehicleId, label, matched })

  it('puts matched vehicles first, each group in natural label order', () => {
    const sorted = sortVehicles([
      v('5010', false),
      v('5003', true),
      v('5002', false),
      v('5011', true),
      v('5004', true),
    ])
    expect(sorted.map((x) => x.vehicleId)).toEqual(['5003', '5004', '5011', '5002', '5010'])
  })

  it('reverses the label order within each group, keeping matched first', () => {
    const sorted = sortVehicles(
      [v('5002', false), v('5003', true), v('5010', false), v('5011', true)],
      true,
    )
    expect(sorted.map((x) => x.vehicleId)).toEqual(['5011', '5003', '5010', '5002'])
  })

  it('orders by label when there is one, numerically', () => {
    const sorted = sortVehicles([v('a', true, 'bus-10'), v('b', true, 'bus-9')])
    expect(sorted.map((x) => x.label)).toEqual(['bus-9', 'bus-10'])
  })
})
