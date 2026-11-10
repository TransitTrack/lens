import { afterEach, describe, expect, it, vi } from 'vitest'
import { draftKeys } from '~/composables/api/keys/drafts'
import { draftGridQuery } from '~/composables/api/queries/drafts'
import { runQuery } from '../../../../helpers/colada'
import { json, stubFetch } from '../../../../helpers/stubFetch'

describe('draftGridQuery', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('requests /drafts/7/grid with the filters and returns the body', async () => {
    const { calls } = stubFetch(() => json({ trips: [] }))
    const options = draftGridQuery({ draftId: 7, routeId: 'r1', directionId: 0, serviceId: 's1' })
    expect(await runQuery(options)).toEqual({ trips: [] })
    const url = new URL(calls[0]!.url)
    expect(url.pathname).toBe('/api/v1/drafts/7/grid')
    expect(Object.fromEntries(url.searchParams)).toEqual({ routeId: 'r1', directionId: '0', serviceId: 's1' })
    expect(options.key).toEqual(['drafts', '7', 'grid', { routeId: 'r1', directionId: 0, serviceId: 's1' }])
    expect(options.key.slice(0, 2)).toEqual(draftKeys.draft('7'))
    expect(options.gcTime).toBe(0)
    expect(options.staleTime).toBe(0)
  })

  it('sends directionId / serviceId only when defined', async () => {
    const { calls } = stubFetch(() => json({}))
    const options = draftGridQuery({ draftId: 7, routeId: 'r1' })
    await runQuery(options)
    expect(Object.fromEntries(new URL(calls[0]!.url).searchParams)).toEqual({ routeId: 'r1' })
    expect(options.key).toEqual(['drafts', '7', 'grid', { routeId: 'r1' }])
  })
})
