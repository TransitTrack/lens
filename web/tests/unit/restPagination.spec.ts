import { describe, expect, it, vi } from 'vitest'
import { fetchAllPages } from '../../app/utils/restPagination'

describe('fetchAllPages', () => {
  it('loads all pages and reads the response total', async () => {
    const loader = vi.fn()
      .mockResolvedValueOnce({ raw: new Response(null, { headers: { 'X-Total-Count': '3' } }), value: async () => [1, 2] })
      .mockResolvedValueOnce({ raw: new Response(null, { headers: { 'X-Total-Count': '3' } }), value: async () => [3] })
    expect(await fetchAllPages(loader, 2)).toEqual([1, 2, 3])
    expect(loader.mock.calls.map((args) => args[0])).toEqual([0, 2])
  })

  it('does not silently truncate when a page disappears', async () => {
    const loader = vi.fn().mockResolvedValue({ raw: new Response(null, { headers: { 'X-Total-Count': '1' } }), value: async () => [] })
    await expect(fetchAllPages(loader, 2)).rejects.toThrow('pagination')
  })
})
