import { afterEach, describe, expect, it, vi } from 'vitest'
import { feedGeometryQuery } from '~/composables/api/queries/schedule'
import { runQuery } from '../../../../helpers/colada'
import { json, stubFetch } from '../../../../helpers/stubFetch'

const collection = { type: 'FeatureCollection', features: [] }

describe('feedGeometryQuery', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('requests /geometry with the scope and returns the collection', async () => {
    const { calls } = stubFetch(() => json(collection))
    const options = feedGeometryQuery({ feedCode: 'f', revisionId: 7 })
    expect(await runQuery(options)).toEqual(collection)
    const url = new URL(calls[0]!.url)
    expect(url.pathname).toBe('/api/v1/feeds/f/geometry')
    expect(url.searchParams.get('revisionId')).toBe('7')
    expect(options.key).toEqual(['feeds', 'f', 'rev', '7', 'geometry', {}])
  })

  it('is fresh for 60 s on the active revision and sends no revisionId', async () => {
    const { calls } = stubFetch(() => json(collection))
    const options = feedGeometryQuery({ feedCode: 'f' })
    await runQuery(options)
    expect(new URL(calls[0]!.url).searchParams.has('revisionId')).toBe(false)
    expect(options.staleTime).toBe(60_000)
  })

  it('never caches a draft', () => {
    const options = feedGeometryQuery({ feedCode: 'f', draftId: 9 })
    expect(options.staleTime).toBe(0)
    expect(options.gcTime).toBe(0)
  })
})
