import { afterEach, describe, expect, it, vi } from 'vitest'
import { validationFindingCountsQuery } from '~/composables/api/queries/gtfs/validationFindingCountsQuery'
import { runQuery } from '../../../../helpers/colada'
import { page, stubFetch } from '../../../../helpers/stubFetch'

const params = { feedCode: 'f', revisionId: 7, entityType: 'stop' }

describe('validationFindingCountsQuery', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('requests /validation-finding-counts with the scope and returns the rows', async () => {
    const { calls } = stubFetch(() => page([{ id: 'x' }]))
    const options = validationFindingCountsQuery(params)
    expect(await runQuery(options)).toEqual([{ id: 'x' }])
    const url = new URL(calls[0]!.url)
    expect(url.pathname).toBe('/api/v1/feeds/f/validation-finding-counts')
    expect(url.searchParams.get('revisionId')).toBe('7')
    expect(url.searchParams.get('entityType')).toBe('stop')
    expect(options.key).toEqual(['feeds', 'f', 'rev', '7', 'validation-finding-counts', {entityType: 'stop'}])
  })

  it('reads every page', async () => {
    const { calls } = stubFetch(({ url }) => new Response(
      JSON.stringify(new URL(url).searchParams.get('offset') === '0' ? [{ id: 'a' }, { id: 'b' }] : [{ id: 'c' }]),
      { headers: { 'Content-Type': 'application/json', 'X-Total-Count': '3' } },
    ))
    expect(await runQuery(validationFindingCountsQuery(params))).toHaveLength(3)
    expect(calls).toHaveLength(2)
  })

  it('sends no revisionId for the active revision', async () => {
    const { calls } = stubFetch(() => page([]))
    await runQuery(validationFindingCountsQuery({ ...params, revisionId: undefined }))
    expect(new URL(calls[0]!.url).searchParams.has('revisionId')).toBe(false)
  })
})
