import { afterEach, describe, expect, it, vi } from 'vitest'
import { validationFindingsQuery } from '~/composables/api/queries/gtfs'
import { runQuery } from '../../../../helpers/colada'
import { page, stubFetch } from '../../../../helpers/stubFetch'

const params = { feedCode: 'f', revisionId: 7, entityType: 'stop', entityId: 'e1' }

describe('validationFindingsQuery', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('requests /validation-findings with the scope and returns the rows', async () => {
    const { calls } = stubFetch(() => page([{ id: 'x' }]))
    const options = validationFindingsQuery(params)
    expect(await runQuery(options)).toEqual([{ id: 'x' }])
    const url = new URL(calls[0]!.url)
    expect(url.pathname).toBe('/api/v1/feeds/f/validation-findings')
    expect(url.searchParams.get('revisionId')).toBe('7')
    expect(url.searchParams.get('entityType')).toBe('stop')
    expect(url.searchParams.get('entityId')).toBe('e1')
    expect(options.key).toEqual(['feeds', 'f', 'rev', '7', 'validation-findings', {entityType: 'stop', entityId: 'e1'}])
  })

  it('reads every page', async () => {
    const { calls } = stubFetch(({ url }) => new Response(
      JSON.stringify(new URL(url).searchParams.get('offset') === '0' ? [{ id: 'a' }, { id: 'b' }] : [{ id: 'c' }]),
      { headers: { 'Content-Type': 'application/json', 'X-Total-Count': '3' } },
    ))
    expect(await runQuery(validationFindingsQuery(params))).toHaveLength(3)
    expect(calls).toHaveLength(2)
  })

  it('sends no revisionId for the active revision', async () => {
    const { calls } = stubFetch(() => page([]))
    await runQuery(validationFindingsQuery({ ...params, revisionId: undefined }))
    expect(new URL(calls[0]!.url).searchParams.has('revisionId')).toBe(false)
  })
})
