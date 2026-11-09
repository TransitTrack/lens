import { afterEach, describe, expect, it, vi } from 'vitest'
import { compareRevisionsQuery } from '~/composables/api/queries/schedule'
import { runQuery } from '../../../../helpers/colada'
import { json, stubFetch } from '../../../../helpers/stubFetch'

const params = { fromRevisionId: 1, toRevisionId: 2, routeId: 'r1', directionId: 0, serviceId: 's1', offset: 0, limit: 200 }

describe('compareRevisionsQuery', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('requests /revision-comparisons with the filters and returns the body', async () => {
    const { calls } = stubFetch(() => json({ tripChangeCount: 0 }))
    const options = compareRevisionsQuery(params)
    expect(await runQuery(options)).toEqual({ tripChangeCount: 0 })
    const url = new URL(calls[0]!.url)
    expect(url.pathname).toBe('/api/v1/revision-comparisons')
    expect(Object.fromEntries(url.searchParams)).toEqual({
      fromRevisionId: '1', toRevisionId: '2', routeId: 'r1', directionId: '0', serviceId: 's1', offset: '0', limit: '200',
    })
    expect(options.key).toEqual(['revision-comparisons', params])
    expect(options.gcTime).toBeUndefined()
  })

  it('omits unset filters', async () => {
    const { calls } = stubFetch(() => json({}))
    await runQuery(compareRevisionsQuery({ fromRevisionId: 1, toRevisionId: 2 }))
    expect(Object.fromEntries(new URL(calls[0]!.url).searchParams)).toEqual({ fromRevisionId: '1', toRevisionId: '2' })
  })

  it('with a draft: key under drafts, never cached', () => {
    const options = compareRevisionsQuery({ ...params, draftId: 9 })
    expect(options.key).toEqual(['drafts', '9', 'compare', params])
    expect(options.gcTime).toBe(0)
    expect(options.staleTime).toBe(0)
  })
})
