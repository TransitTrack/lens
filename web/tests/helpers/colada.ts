import type { useQueryCache } from '@pinia/colada'
import { vi } from 'vitest'

/** Run a query's `query` function the way Pinia Colada would, without mounting anything. */
export function runQuery<T>(options: {
  key: readonly unknown[]
  query: (ctx: { signal: AbortSignal }) => Promise<T>
}): Promise<T> {
  return options.query({ signal: new AbortController().signal })
}

/** A stand-in for `useQueryCache()` so mutation tests can assert on `invalidateQueries`. */
export function fakeQueryCache() {
  const invalidateQueries = vi.fn(async () => [])
  return { invalidateQueries } as unknown as ReturnType<typeof useQueryCache> & {
    invalidateQueries: typeof invalidateQueries
  }
}
