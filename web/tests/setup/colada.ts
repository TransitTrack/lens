import { afterEach } from 'vitest'
import { useQueryCache } from '@pinia/colada'
import { getActivePinia } from 'pinia'

// The query cache lives in the app's Pinia; without this, one test's entries leak into the next.
// Specs that run outside the Nuxt environment have no Pinia, hence the guard.
afterEach(() => {
  if (!getActivePinia()) return
  const cache = useQueryCache()
  for (const entry of cache.getEntries()) cache.remove(entry)
})
