import { QueryClient } from '@tanstack/vue-query'

/**
 * The app's single TanStack Query cache. The app is client-only (`ssr: false`), so a module-level
 * instance is safe; composables pass it explicitly so they work without the Nuxt plugin installed
 * (unit tests, effect scopes outside a component).
 *
 * Defaults keep the previous hand-rolled behaviour: no automatic retries, no refetch on focus or
 * reconnect, and always revalidate on mount (`staleTime: 0`) while still showing cached data first.
 */
export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: false,
      staleTime: 0,
      refetchOnWindowFocus: false,
      refetchOnReconnect: false,
    },
  },
})
