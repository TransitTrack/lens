import { describe, expect, it, vi } from 'vitest'
import { fetchAllVehicles } from '../../app/utils/paginatedVehicles'

describe('fetchAllVehicles', () => {
  it('reads every vehicle from the feed map endpoint', async () => {
    const fetcher = vi.fn().mockResolvedValue(new Response(JSON.stringify([{ vehicleId: '1' }, { vehicleId: '2' }])))
    const result = await fetchAllVehicles('feed / one', new AbortController().signal, fetcher)
    expect(result.map((v) => v.vehicleId)).toEqual(['1', '2'])
    expect(fetcher.mock.calls[0]![0]).toBe('/api/feeds/feed%20%2F%20one/vehicles')
  })

  it('rejects failed or malformed responses', async () => {
    const failed = vi.fn().mockResolvedValue(new Response('boom', { status: 500 }))
    await expect(fetchAllVehicles('feed', new AbortController().signal, failed)).rejects.toThrow('500')
    const malformed = vi.fn().mockResolvedValue(new Response('{}'))
    await expect(fetchAllVehicles('feed', new AbortController().signal, malformed)).rejects.toThrow('Invalid')
  })
})
