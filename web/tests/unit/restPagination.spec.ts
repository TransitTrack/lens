import { describe, expect, it, vi } from 'vitest'
import { fetchAllPages } from '../../app/utils/restPagination'

const page = <T>(data: T[], total: number) => ({ data, response: new Response(null, { headers: { 'X-Total-Count': String(total) } }) })

describe('fetchAllPages', () => {
  it('loads all pages and reads the response total', async () => {
    const loader = vi.fn()
      .mockResolvedValueOnce(page([1, 2], 3))
      .mockResolvedValueOnce(page([3], 3))
    expect(await fetchAllPages(loader, 2)).toEqual([1, 2, 3])
    expect(loader.mock.calls.map((args) => args[0])).toEqual([0, 2])
  })

  it('does not silently truncate when a page disappears', async () => {
    const loader = vi.fn().mockResolvedValue(page([], 1))
    await expect(fetchAllPages(loader, 2)).rejects.toThrow('pagination')
  })

  it('rejects a missing total', async () => {
    const loader = vi.fn().mockResolvedValue({ data: [1], response: new Response(null) })
    await expect(fetchAllPages(loader, 2)).rejects.toThrow('X-Total-Count')
  })
})
