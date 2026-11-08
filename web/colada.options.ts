import type { PiniaColadaOptions } from '@pinia/colada'
import { PiniaColadaAutoRefetch } from '@pinia/colada-plugin-auto-refetch'

// Mirrors the defaults in app/api/queryClient.ts. Polling is opt-in per query via `autoRefetch`.
export default {
  queryOptions: {
    staleTime: 0,
    refetchOnWindowFocus: false,
    refetchOnReconnect: false,
    // Keep the previous key's data on screen while the next key loads.
    placeholderData: previous => previous,
  },
  plugins: [PiniaColadaAutoRefetch({ autoRefetch: false })],
} satisfies PiniaColadaOptions
