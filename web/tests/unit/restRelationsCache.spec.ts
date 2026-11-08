import { afterEach, describe, expect, it, vi } from 'vitest'
import { blockOutline, routeStops, stopPathsByPattern } from '../../app/utils/restRelations'
import { json, page, stubFetch } from '../helpers/stubFetch'

// The pinned cache is module-global, so every test uses its own feed code.
describe('revision-pinned reads', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('reads a route\'s stops once per revision', async () => {
    const { fetcher } = stubFetch(() => page([{ stopId: 'S1' }]))
    const scope = { feedCode: 'c1', routeId: 'R', revisionId: 51 }

    const first = await routeStops(scope)
    const second = await routeStops(scope)

    expect(first.get('S1')).toEqual({ stopId: 'S1' })
    expect(second).toBe(first)
    expect(fetcher).toHaveBeenCalledTimes(1)
    await routeStops({ ...scope, revisionId: 52 })
    expect(fetcher).toHaveBeenCalledTimes(2)
  })

  it('does not keep reads that mean "the active revision"', async () => {
    const { fetcher } = stubFetch(() => page([{ stopId: 'S1' }]))

    await routeStops({ feedCode: 'c2', routeId: 'R' })
    await routeStops({ feedCode: 'c2', routeId: 'R' })

    expect(fetcher).toHaveBeenCalledTimes(2)
  })

  it('reads a pattern\'s stop paths and a route\'s stops once, however often the vehicle polls', async () => {
    const { fetcher } = stubFetch(({ url }) =>
      url.includes('/stop-paths') ? page([{ patternKey: 'P', stopId: 'S1' }]) : page([{ stopId: 'S1' }]))
    const scope = { feedCode: 'c3', revisionId: '51', patternKey: 'P', routeId: 'R' }

    await stopPathsByPattern(scope)
    const again = await stopPathsByPattern(scope)

    expect(again.get('P')![0]!.stop).toEqual({ stopId: 'S1' })
    expect(fetcher).toHaveBeenCalledTimes(2)
  })

  it('reads a block and its trips once per revision', async () => {
    const { fetcher } = stubFetch(({ url }) =>
      url.includes('/block-trips') ? page([{ listIndex: 0, tripId: 'T1' }]) : json({ blockId: 'B' }))
    const scope = { feedCode: 'c4', revisionId: 51, blockId: 'B', serviceId: 'WK' }

    await blockOutline(scope)
    const again = await blockOutline(scope)

    expect(again?.blockTrips[0]?.trip.tripId).toBe('T1')
    expect(fetcher).toHaveBeenCalledTimes(2)
  })

  it('forgets a failed read so the next call retries', async () => {
    let fail = true
    const { fetcher } = stubFetch(() => (fail ? new Response('boom', { status: 500 }) : page([{ stopId: 'S1' }])))
    const scope = { feedCode: 'c5', routeId: 'R', revisionId: 51 }

    await expect(routeStops(scope)).rejects.toBeDefined()
    fail = false
    expect((await routeStops(scope)).get('S1')).toBeDefined()
    expect(fetcher).toHaveBeenCalledTimes(2)
  })
})
