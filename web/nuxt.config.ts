export default defineNuxtConfig({
  compatibilityDate: '2026-09-06',
  // This dashboard is a client-side-polling ops UI (see spec: no SSR data-fetching
  // is needed or wanted). Disabling SSR also sidesteps a real crash class where
  // universal code (e.g. the Apollo plugin) runs during server rendering with no
  // browser origin to resolve relative GraphQL URIs against.
  ssr: false,
  modules: ['@nuxt/ui', '@nuxt/eslint', 'nuxt-maplibre', '@vueuse/nuxt'],
  runtimeConfig: {
    public: {
      // Live-dashboard poll interval (ms). Overridable at deploy time via
      // NUXT_PUBLIC_POLL_INTERVAL_MS - no rebuild needed. See usePollControl.ts.
      pollIntervalMs: 5000,
      // Frontend-only demo gate. These values are intentionally public and must
      // be replaced with server-side authentication before a production deployment.
      demoAuthUsername: 'operator',
      demoAuthPassword: 'transittrack',
    },
  },
  css: ['~/assets/css/main.css'],
  devtools: { enabled: true },
  vite: {
    optimizeDeps: {
      exclude: ['maplibre-gl'],
    },
  },
  nitro: {
    devProxy: {
      '/graphql': {
        target: process.env.NUXT_BACKEND_URL ?? 'http://localhost:8080/graphql',
        changeOrigin: true,
      },
    },
  },
})
