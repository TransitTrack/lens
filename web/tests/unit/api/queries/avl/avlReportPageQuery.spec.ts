import { afterEach, describe, expect, it, vi } from 'vitest'
import { avlReportPageQuery } from '~/composables/api/queries/avl'
import { runQuery } from '../../../../helpers/colada'
import { json, stubFetch } from '../../../../helpers/stubFetch'

const params = { avlFeedCode: 'a', vehicleId: 'v1', date: '2026-01-02', startTime: '08:00', endTime: '09:00', page: 2, pageSize: 20 }

describe('avlReportPageQuery', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('requests /feeds/a/avl/vehicles/v1/report-page and returns the result', async () => {
    const { calls } = stubFetch(() => json({ id: 'x' }))
    const options = avlReportPageQuery(params)
    expect(await runQuery(options)).toEqual({ id: 'x' })
    const url = new URL(calls[0]!.url)
    expect(url.pathname).toBe('/api/v1/feeds/a/avl/vehicles/v1/report-page')
    expect(url.searchParams.get('date')).toBe('2026-01-02')
    expect(url.searchParams.get('startTime')).toBe('08:00')
    expect(url.searchParams.get('endTime')).toBe('09:00')
    expect(url.searchParams.get('page')).toBe('2')
    expect(url.searchParams.get('pageSize')).toBe('20')
    expect(options.key).toEqual(['avl', 'a', 'report-page', { vehicleId: 'v1', date: '2026-01-02', startTime: '08:00', endTime: '09:00', page: 2, pageSize: 20 }])
  })
})
