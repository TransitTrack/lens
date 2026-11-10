import { describe, expect, it } from 'vitest'
import type { AvlFeed, AvlReportMatch, Block, BlockTrip, Feed, Route, Stop, StopPath, Trip } from '~/api'
import { blocksWithTrips, blockTripsWithTrips } from '~/utils/joins/blocks'
import { agenciesExtent } from '~/utils/joins/extent'
import { activeRevision, annotateFeeds, gtfsFeedCodeOf } from '~/utils/joins/feeds'
import { reportRouteIdsByRevision, reportsWithTrips, reportsWithTripsByRevision, reportTripKeys } from '~/utils/joins/reports'
import { indexBy, withRoute } from '~/utils/joins/routes'
import { groupStopPaths, patternsWithStopPaths } from '~/utils/joins/stopPaths'

const as = <T>(v: unknown) => v as T

describe('stop paths', () => {
  const paths = as<StopPath[]>([
    { patternKey: 'p1', stopId: 'S1' }, { patternKey: 'p1', stopId: 'S9' }, { patternKey: 'p2', stopId: 'S1' },
  ])
  const stops = as<Stop[]>([{ stopId: 'S1' }])

  it('groups by pattern key and sets stop to null when missing', () => {
    const grouped = groupStopPaths(paths, stops)
    expect(grouped.get('p1')).toEqual([
      { patternKey: 'p1', stopId: 'S1', stop: { stopId: 'S1' } },
      { patternKey: 'p1', stopId: 'S9', stop: null },
    ])
    expect(grouped.get('p2')).toHaveLength(1)
  })

  it('accepts a stop map and attaches paths to patterns', () => {
    const grouped = groupStopPaths(paths, new Map([['S1', stops[0]!]]))
    const out = patternsWithStopPaths([{ patternKey: 'p1' }, { patternKey: 'none' }], grouped)
    expect(out[0]!.stopPaths).toHaveLength(2)
    expect(out[1]!.stopPaths).toEqual([])
  })
})

describe('routes', () => {
  it('indexes and joins routes', () => {
    const routes = as<Route[]>([{ routeId: 'R1', agencyId: 'A' }, { routeId: 'R2', agencyId: 'X' }, { routeId: 'R3' }])
    const map = indexBy(routes, 'routeId')
    expect(withRoute({ routeId: 'R1' }, map).route).toBe(routes[0])
    expect(withRoute({ routeId: 'zz' }, map).route).toBeNull()
  })
})

describe('blocks', () => {
  it('groups trips by serviceId|blockId and skips trips without a block', () => {
    const blocks = as<Block[]>([{ serviceId: 'W', blockId: 'B1' }, { serviceId: 'S', blockId: 'B1' }])
    const trips = as<Trip[]>([
      { tripId: 't1', serviceId: 'W', blockId: 'B1' }, { tripId: 't2', serviceId: 'W' }, { tripId: 't3', serviceId: 'S', blockId: 'B1' },
    ])
    const out = blocksWithTrips(blocks, trips)
    expect(out[0]!.trips.map((t) => t.tripId)).toEqual(['t1'])
    expect(out[1]!.trips.map((t) => t.tripId)).toEqual(['t3'])
  })

  it('joins block trips with trip and route, dropping unknown trips', () => {
    const bts = as<BlockTrip[]>([{ tripId: 't1' }, { tripId: 'gone' }])
    const route = as<Route>({ routeId: 'R1' })
    const out = blockTripsWithTrips(bts, as<Trip[]>([{ tripId: 't1', routeId: 'R1' }]), new Map([['R1', route]]))
    expect(out).toEqual([{ tripId: 't1', trip: { tripId: 't1', routeId: 'R1', route } }])
  })
})

describe('reports', () => {
  const m = (revisionId: string, tripId: string) => as<AvlReportMatch>({ revisionId, tripId })
  const reports = [{ id: 1, match: m('1', 'a') }, { id: 2, match: null }, { id: 3, match: m('1', 'a') }, { id: 4, match: m('2', 'b') }]

  it('lists distinct trip keys', () => {
    expect(reportTripKeys(reports)).toEqual([{ revisionId: '1', tripId: 'a' }, { revisionId: '2', tripId: 'b' }])
  })

  it('joins trips and routes', () => {
    const trip = as<Trip>({ tripId: 'a', routeId: 'R1' })
    const route = as<Route>({ routeId: 'R1' })
    const trips = new Map<string, Trip | null>([['1|a', trip], ['2|b', null]])
    const plain = reportsWithTrips(reports, trips)
    expect(plain[0]!.match).toEqual({ revisionId: '1', tripId: 'a', trip })
    expect(plain[1]!.match).toBeNull()
    expect(plain[3]!.match).toEqual({ revisionId: '2', tripId: 'b', trip: null })
    const routed = reportsWithTrips(reports, trips, new Map([['R1', route]]))
    expect(routed[0]!.match).toMatchObject({ trip: { tripId: 'a', route } })
    expect(routed[3]!.match).toMatchObject({ trip: null })
  })

  it('lists each revision\'s route ids from the matched trips', () => {
    const trips = new Map<string, Trip | null>([
      ['1|a', as<Trip>({ tripId: 'a', routeId: 'R1' })], ['1|c', as<Trip>({ tripId: 'c', routeId: 'R1' })],
      ['2|b', as<Trip>({ tripId: 'b', routeId: 'R1' })], ['2|x', null],
    ])
    expect(reportRouteIdsByRevision(trips)).toEqual({ 1: ['R1'], 2: ['R1'] })
  })

  it('joins each report with its own revision\'s route, keeping the report order', () => {
    const trips = new Map<string, Trip | null>([['1|a', as<Trip>({ tripId: 'a', routeId: 'R1' })], ['2|b', as<Trip>({ tripId: 'b', routeId: 'R1' })]])
    const routes = new Map([
      ['1', new Map([['R1', as<Route>({ routeId: 'R1', routeShortName: 'old' })]])],
      ['2', new Map([['R1', as<Route>({ routeId: 'R1', routeShortName: 'new' })]])],
    ])
    const joined = reportsWithTripsByRevision(reports, trips, routes)
    expect(joined.map((r) => r.id)).toEqual([1, 2, 3, 4])
    expect(joined.map((r) => r.match?.trip?.route?.routeShortName ?? null)).toEqual(['old', null, 'old', 'new'])
    const unknown = reportsWithTripsByRevision(reports, trips, new Map())
    expect(unknown[0]!.match).toMatchObject({ trip: { tripId: 'a', route: null } })
  })
})

describe('feeds', () => {
  const gtfs = as<Feed[]>([{ code: 'a', name: 'A', enabled: true }, { code: 'b', name: 'B', enabled: true, description: 'd' }])
  const avl = as<AvlFeed[]>([{ code: 'av', gtfsFeedCode: 'a', lastPollStatus: 'SUCCESS', predictionAlgorithms: ['x'] }])

  it('annotates feeds and marks realtime health', () => {
    const [a, b] = annotateFeeds(gtfs, avl)
    expect(a).toMatchObject({ avlFeedCode: 'av', hasRealtime: true, realtimeHealthy: true, predictionAlgorithms: ['x'], description: null })
    expect(b).toMatchObject({ avlFeedCode: null, hasRealtime: false, realtimeHealthy: false, description: 'd' })
    expect(annotateFeeds(gtfs, [{ ...avl[0]!, lastPollStatus: 'ERROR' }])[0]!.realtimeHealthy).toBe(false)
    for (const status of ['ok', 'Healthy']) {
      expect(annotateFeeds(gtfs, [{ ...avl[0]!, lastPollStatus: status }])[0]!.realtimeHealthy).toBe(true)
    }
  })

  it('finds the active revision and the gtfs feed of an avl feed', () => {
    expect(activeRevision(as([{ id: '1', status: 'SUPERSEDED' }, { id: '2', status: 'ACTIVE' }]))?.id).toBe('2')
    expect(activeRevision([])).toBeNull()
    expect(gtfsFeedCodeOf(avl, 'av')).toBe('a')
    expect(gtfsFeedCodeOf(avl, 'nope')).toBeNull()
  })
})

describe('agenciesExtent', () => {
  it('combines extents, or null when none', () => {
    expect(agenciesExtent([])).toBeNull()
    expect(agenciesExtent(as([{ extent: null }]))).toBeNull()
    expect(agenciesExtent(as([
      { extent: { minLon: 1, minLat: 2, maxLon: 3, maxLat: 4 } }, { extent: { minLon: 0, minLat: 3, maxLon: 5, maxLat: 6 } }, {},
    ]))).toEqual({ minLon: 0, minLat: 2, maxLon: 5, maxLat: 6 })
  })
})
