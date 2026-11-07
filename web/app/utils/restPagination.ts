import type { ApiResponse } from '~~/generated/rest/runtime'

/** Read every page, rejecting inconsistent totals rather than silently dropping rows. */
export async function fetchAllPages<T>(load: (offset: number, limit: number) => Promise<ApiResponse<T[]>>, limit = 500): Promise<T[]> {
  const rows: T[] = []
  let total: number
  do {
    const response = await load(rows.length, limit)
    const rawTotal = response.raw.headers.get('X-Total-Count')
    if (rawTotal === null || !/^\d+$/.test(rawTotal)) throw new Error('Invalid X-Total-Count header')
    total = Number(rawTotal)
    if (!Number.isSafeInteger(total)) throw new Error('Invalid X-Total-Count header')
    const page = await response.value()
    if (!Array.isArray(page)) throw new Error('Invalid page response')
    if (page.length === 0 && rows.length < total) throw new Error('pagination changed during the request')
    rows.push(...page)
  } while (rows.length < total)
  return rows
}
