import { describe, expect, it } from 'vitest'
import { deriveFeatures, routeTypeBreakdown } from '../../app/utils/feedFeatures'

describe('deriveFeatures', () => {
  const feature = (out: ReturnType<typeof deriveFeatures>, key: string) =>
    out.find((f) => f.key === key)!

  it('flags shapes / fares / pathways from filesPresent', () => {
    const out = deriveFeatures({
      filesPresent: ['shapes.txt', 'fare_products.txt', 'pathways.txt'],
      rowCounts: {},
      routes: [],
    })
    expect(feature(out, 'shapes').present).toBe(true)
    expect(feature(out, 'fares').present).toBe(true)
    expect(feature(out, 'pathways').present).toBe(true)
    expect(feature(out, 'transfers').present).toBe(false)
  })

  it('service calendar is present when either calendar file exists', () => {
    expect(
      feature(deriveFeatures({ filesPresent: ['calendar_dates.txt'], rowCounts: {}, routes: [] }), 'calendar')
        .present,
    ).toBe(true)
  })

  it('route colors come from parsed routes, not files', () => {
    expect(
      feature(deriveFeatures({ filesPresent: [], rowCounts: {}, routes: [{ routeColor: 'FF0000' }] }), 'colors')
        .present,
    ).toBe(true)
    expect(
      feature(deriveFeatures({ filesPresent: [], rowCounts: {}, routes: [{ routeColor: null }] }), 'colors')
        .present,
    ).toBe(false)
  })

  it('tolerates null inputs', () => {
    const out = deriveFeatures({ filesPresent: null, rowCounts: null, routes: [] })
    expect(out.every((f) => f.present === false)).toBe(true)
  })
})

describe('routeTypeBreakdown', () => {
  it('counts per route_type, most common first', () => {
    const out = routeTypeBreakdown([
      { routeType: 3 },
      { routeType: 3 },
      { routeType: 0 },
      { routeType: 11 },
      { routeType: 3 },
    ])
    expect(out).toEqual([
      { type: 3, label: 'Bus', count: 3 },
      { type: 0, label: 'Tram', count: 1 },
      { type: 11, label: 'Trolleybus', count: 1 },
    ])
  })

  it('ignores null route types', () => {
    expect(routeTypeBreakdown([{ routeType: null }, { routeType: undefined }])).toEqual([])
  })
})
