import { afterEach, describe, expect, it, vi } from 'vitest'
import { tripQuery } from '~/composables/api/queries/gtfs'
import { runQuery } from '../../../../helpers/colada'
import { json, stubFetch } from '../../../../helpers/stubFetch'

const params = { feedCode: 'f', revisionId: 7, tripId: 't1' }

describe('tripQuery', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('requests /trips/t1 with the scope and returns the row', async () => {
    const { calls } = stubFetch(() => json({ id: 'x' }))
    const options = tripQuery(params)
    expect(await runQuery(options)).toEqual({ id: 'x' })
    const url = new URL(calls[0]!.url)
    expect(url.pathname).toBe('/api/v1/feeds/f/trips/t1')
    expect(url.searchParams.get('revisionId')).toBe('7')
    expect(options.key).toEqual(['feeds', 'f', 'rev', '7', 'trips', {tripId: 't1'}])
  })

  it('returns null on 404', async () => {
    stubFetch(() => json({ title: 'Not Found', status: 404 }, 404))
    expect(await runQuery(tripQuery(params))).toBeNull()
  })

  it('sends no revisionId for the active revision', async () => {
    const { calls } = stubFetch(() => json({}))
    await runQuery(tripQuery({ ...params, revisionId: undefined }))
    expect(new URL(calls[0]!.url).searchParams.has('revisionId')).toBe(false)
  })
})
