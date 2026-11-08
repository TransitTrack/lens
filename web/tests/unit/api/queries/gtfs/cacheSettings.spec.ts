import { afterEach, describe, expect, it, vi } from 'vitest'
import { routesQuery } from '~/composables/api/queries/gtfs/routesQuery'
import { tripQuery } from '~/composables/api/queries/gtfs/tripQuery'
import { feedInfoQuery } from '~/composables/api/queries/gtfs/feedInfoQuery'
import { runQuery } from '../../../../helpers/colada'
import { json, stubFetch } from '../../../../helpers/stubFetch'

describe('GTFS query cache settings', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('routesQuery is immutable with a revisionId and fresh for 60 s without', () => {
    expect(routesQuery({ feedCode: 'f', revisionId: 3 }).staleTime).toBe(Infinity)
    expect(routesQuery({ feedCode: 'f' }).staleTime).toBe(60_000)
  })

  it('other reads inherit the default on the active revision', () => {
    expect(feedInfoQuery({ feedCode: 'f' }).staleTime).toBeUndefined()
    expect(feedInfoQuery({ feedCode: 'f' }).gcTime).toBeUndefined()
  })

  it('a draft read sends the draft id, lives under the draft key and is never kept', async () => {
    const { calls } = stubFetch(() => json({ tripId: 't' }))
    const options = tripQuery({ feedCode: 'f', revisionId: 9, draftId: 9, tripId: 't' })
    await runQuery(options)
    expect(new URL(calls[0]!.url).searchParams.get('revisionId')).toBe('9')
    expect(options.key.slice(0, 2)).toEqual(['drafts', '9'])
    expect(options.gcTime).toBe(0)
    expect(options.staleTime).toBe(0)
  })
})
