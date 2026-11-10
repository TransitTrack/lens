import { afterEach, describe, expect, it, vi } from 'vitest'
import { shapeQuery } from '~/composables/api/queries/gtfs'
import { runQuery } from '../../../../helpers/colada'
import { json, stubFetch } from '../../../../helpers/stubFetch'

const params = { feedCode: 'f', revisionId: 7, shapeId: 'sh1' }

describe('shapeQuery', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('requests /shapes/sh1 with the scope and returns the row', async () => {
    const { calls } = stubFetch(() => json({ id: 'x' }))
    const options = shapeQuery(params)
    expect(await runQuery(options)).toEqual({ id: 'x' })
    const url = new URL(calls[0]!.url)
    expect(url.pathname).toBe('/api/v1/feeds/f/shapes/sh1')
    expect(url.searchParams.get('revisionId')).toBe('7')
    expect(options.key).toEqual(['feeds', 'f', 'rev', '7', 'shapes', {shapeId: 'sh1'}])
  })

  it('returns null on 404', async () => {
    stubFetch(() => json({ title: 'Not Found', status: 404 }, 404))
    expect(await runQuery(shapeQuery(params))).toBeNull()
  })

  it('sends no revisionId for the active revision', async () => {
    const { calls } = stubFetch(() => json({}))
    await runQuery(shapeQuery({ ...params, revisionId: undefined }))
    expect(new URL(calls[0]!.url).searchParams.has('revisionId')).toBe(false)
  })
})
