import { afterEach, describe, expect, it, vi } from 'vitest'
import { tripPatternQuery } from '~/composables/api/queries/schedule'
import { runQuery } from '../../../../helpers/colada'
import { json, stubFetch } from '../../../../helpers/stubFetch'

const params = { feedCode: 'f', revisionId: 7, patternKey: 'pk' }

describe('tripPatternQuery', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('requests /trip-patterns/pk with the scope and returns the row', async () => {
    const { calls } = stubFetch(() => json({ id: 'x' }))
    const options = tripPatternQuery(params)
    expect(await runQuery(options)).toEqual({ id: 'x' })
    const url = new URL(calls[0]!.url)
    expect(url.pathname).toBe('/api/v1/feeds/f/trip-patterns/pk')
    expect(url.searchParams.get('revisionId')).toBe('7')
    expect(options.key).toEqual(['feeds', 'f', 'rev', '7', 'trip-patterns', { patternKey: 'pk' }])
  })

  it('returns null on 404', async () => {
    stubFetch(() => json({ title: 'Not Found', status: 404 }, 404))
    expect(await runQuery(tripPatternQuery(params))).toBeNull()
  })

  it('sends no revisionId for the active revision', async () => {
    const { calls } = stubFetch(() => json({}))
    await runQuery(tripPatternQuery({ ...params, revisionId: undefined }))
    expect(new URL(calls[0]!.url).searchParams.has('revisionId')).toBe(false)
  })
})
