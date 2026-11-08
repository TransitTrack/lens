import { afterEach, describe, expect, it, vi } from 'vitest'
import { routeQuery } from '~/composables/api/queries/gtfs/routeQuery'
import { runQuery } from '../../../../helpers/colada'
import { json, stubFetch } from '../../../../helpers/stubFetch'

const params = { feedCode: 'f', revisionId: 7, routeId: 'r1' }

describe('routeQuery', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('requests /routes/r1 with the scope and returns the row', async () => {
    const { calls } = stubFetch(() => json({ id: 'x' }))
    const options = routeQuery(params)
    expect(await runQuery(options)).toEqual({ id: 'x' })
    const url = new URL(calls[0]!.url)
    expect(url.pathname).toBe('/api/v1/feeds/f/routes/r1')
    expect(url.searchParams.get('revisionId')).toBe('7')
    expect(options.key).toEqual(['feeds', 'f', 'rev', '7', 'routes', {routeId: 'r1'}])
  })

  it('returns null on 404', async () => {
    stubFetch(() => json({ title: 'Not Found', status: 404 }, 404))
    expect(await runQuery(routeQuery(params))).toBeNull()
  })

  it('sends no revisionId for the active revision', async () => {
    const { calls } = stubFetch(() => json({}))
    await runQuery(routeQuery({ ...params, revisionId: undefined }))
    expect(new URL(calls[0]!.url).searchParams.has('revisionId')).toBe(false)
  })
})
