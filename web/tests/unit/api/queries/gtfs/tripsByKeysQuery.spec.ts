import { afterEach, describe, expect, it, vi } from 'vitest'
import { tripsByKeysQuery } from '~/composables/api/queries/gtfs/tripsByKeysQuery'
import { reportsWithTrips } from '~/utils/joins/reports'
import { runQuery } from '../../../../helpers/colada'
import { json, stubFetch } from '../../../../helpers/stubFetch'

describe('tripsByKeysQuery', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('makes one request per distinct key and returns a map keyed revisionId|tripId', async () => {
    const { calls } = stubFetch(({ url }) => json({ tripId: new URL(url).pathname.split('/').pop() }))
    const keys = [
      { revisionId: '2', tripId: 'b' },
      { revisionId: '1', tripId: 'a' },
      { revisionId: '2', tripId: 'b' },
    ]
    const options = tripsByKeysQuery({ feedCode: 'f', keys })
    const result = await runQuery(options)
    expect(calls).toHaveLength(2)
    const urls = calls.map((c) => new URL(c.url))
    expect(urls.map((u) => `${u.pathname}?${u.searchParams.get('revisionId')}`).sort()).toEqual([
      '/api/v1/feeds/f/trips/a?1',
      '/api/v1/feeds/f/trips/b?2',
    ])
    expect([...result.keys()].sort()).toEqual(['1|a', '2|b'])
    expect(result.get('2|b')).toEqual({ tripId: 'b' })
    expect(options.key).toEqual(['feeds', 'f', 'trips-by-keys', { keys: ['1|a', '2|b'] }])
    expect(options.staleTime).toBe(Infinity)
  })

  it('gives the same key for the same keys in any order', () => {
    const a = { revisionId: '1', tripId: 'a' }
    const b = { revisionId: '1', tripId: 'b' }
    expect(tripsByKeysQuery({ feedCode: 'f', keys: [a, b] }).key).toEqual(tripsByKeysQuery({ feedCode: 'f', keys: [b, a] }).key)
  })

  it('maps a 404 to null so reportsWithTrips yields a null trip', async () => {
    stubFetch(() => json({ title: 'Not Found', status: 404 }, 404))
    const trips = await runQuery(tripsByKeysQuery({ feedCode: 'f', keys: [{ revisionId: '1', tripId: 'gone' }] }))
    expect(trips.get('1|gone')).toBeNull()
    const [row] = reportsWithTrips([{ match: { revisionId: '1', tripId: 'gone' } as never }], trips)
    expect(row!.match).toMatchObject({ trip: null })
  })

  it('maps any other failed trip read to null and keeps the other trips', async () => {
    stubFetch(({ url }) =>
      new URL(url).pathname.endsWith('/bad') ? json({ title: 'Boom', status: 500 }, 500) : json({ tripId: 'ok' }))
    const trips = await runQuery(tripsByKeysQuery({
      feedCode: 'f',
      keys: [{ revisionId: '1', tripId: 'bad' }, { revisionId: '1', tripId: 'ok' }],
    }))
    expect(trips.get('1|bad')).toBeNull()
    expect(trips.get('1|ok')).toEqual({ tripId: 'ok' })
  })

  it('rejects when the read is cancelled instead of resolving to nulls', async () => {
    stubFetch(() => json({ tripId: 'a' }))
    const controller = new AbortController()
    controller.abort()
    const options = tripsByKeysQuery({ feedCode: 'f', keys: [{ revisionId: '1', tripId: 'a' }] })
    await expect(options.query({ signal: controller.signal } as never)).rejects.toBeDefined()
  })

  it('makes no request for an empty key list, and reports without a match get match: null', async () => {
    const { calls } = stubFetch(() => json({}))
    const trips = await runQuery(tripsByKeysQuery({ feedCode: 'f', keys: [] }))
    expect(calls).toHaveLength(0)
    expect(trips.size).toBe(0)
    expect(reportsWithTrips([{ match: null }, {}], trips).map((r) => r.match)).toEqual([null, null])
  })
})
