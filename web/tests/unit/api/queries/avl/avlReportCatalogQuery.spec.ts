import { afterEach, describe, expect, it, vi } from 'vitest'
import { avlReportCatalogQuery } from '~/composables/api/queries/avl/avlReportCatalogQuery'
import { runQuery } from '../../../../helpers/colada'
import { json, stubFetch } from '../../../../helpers/stubFetch'

const params = { avlFeedCode: 'a', }

describe('avlReportCatalogQuery', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('requests /feeds/a/avl/report-catalog and returns the result', async () => {
    const { calls } = stubFetch(() => json({ id: 'x' }))
    const options = avlReportCatalogQuery(params)
    expect(await runQuery(options)).toEqual({ id: 'x' })
    const url = new URL(calls[0]!.url)
    expect(url.pathname).toBe('/api/v1/feeds/a/avl/report-catalog')
    expect(options.key).toEqual(['avl', 'a', 'report-catalog', {}])
  })
})
