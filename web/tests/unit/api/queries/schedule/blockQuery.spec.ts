import { afterEach, describe, expect, it, vi } from 'vitest'
import { blockQuery } from '~/composables/api/queries/schedule'
import { runQuery } from '../../../../helpers/colada'
import { json, stubFetch } from '../../../../helpers/stubFetch'

const params = { feedCode: 'f', revisionId: 7, blockId: 'b1', serviceId: 's1' }

describe('blockQuery', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('requests /blocks/b1 with the scope and returns the row', async () => {
    const { calls } = stubFetch(() => json({ id: 'x' }))
    const options = blockQuery(params)
    expect(await runQuery(options)).toEqual({ id: 'x' })
    const url = new URL(calls[0]!.url)
    expect(url.pathname).toBe('/api/v1/feeds/f/blocks/b1')
    expect(url.searchParams.get('revisionId')).toBe('7')
    expect(url.searchParams.get('serviceId')).toBe('s1')
    expect(options.key).toEqual(['feeds', 'f', 'rev', '7', 'blocks', { blockId: 'b1', serviceId: 's1' }])
  })

  it('returns null on 404', async () => {
    stubFetch(() => json({ title: 'Not Found', status: 404 }, 404))
    expect(await runQuery(blockQuery(params))).toBeNull()
  })

  it('sends no revisionId for the active revision', async () => {
    const { calls } = stubFetch(() => json({}))
    await runQuery(blockQuery({ ...params, revisionId: undefined }))
    expect(new URL(calls[0]!.url).searchParams.has('revisionId')).toBe(false)
  })
})
