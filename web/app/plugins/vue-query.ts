import { VueQueryPlugin } from '@tanstack/vue-query'
import type { QueryClient } from '@tanstack/vue-query'
import { queryClient } from '~/api/queryClient'

declare global {
  interface Window {
    __TANSTACK_QUERY_CLIENT__?: QueryClient
  }
}

export default defineNuxtPlugin((nuxtApp) => {
  nuxtApp.vueApp.use(VueQueryPlugin, { queryClient })
  // The TanStack Query browser DevTools extension finds the cache through this global.
  if (import.meta.dev) window.__TANSTACK_QUERY_CLIENT__ = queryClient
})
