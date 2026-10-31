import { fileURLToPath } from 'node:url'
import { rehypeMermaid, remarkDocLinks } from './content-plugins/docsMarkdown'

export default defineNuxtConfig({
  compatibilityDate: '2026-09-06',
  // This dashboard is a client-side-polling ops UI (see spec: no SSR data-fetching
  // is needed or wanted). Disabling SSR also sidesteps a real crash class where
  // universal code (e.g. the Apollo plugin) runs during server rendering with no
  // browser origin to resolve relative GraphQL URIs against.
  ssr: false,
  modules: ['@nuxt/ui', '@nuxt/eslint', 'nuxt-maplibre', '@vueuse/nuxt', '@nuxt/content'],
  // Renders the repo's docs/*.md (collection in content.config.ts) at build time.
  // node:sqlite avoids a native better-sqlite3 build; the browser queries the
  // generated dump through WASM SQLite, which is what makes this work with ssr: false.
  content: {
    experimental: { sqliteConnector: 'native' },
    build: {
      markdown: {
        // Two independent things read this config, each keying off a different field:
        // `@nuxt/content`'s own markdown transformer (the one that actually turns docs/*.md
        // into the collection — `importPlugins` in its module.mjs) uses `instance` directly
        // when present; its generated `<MDC>`-component codegen (@nuxtjs/mdc's
        // `processUnistPlugins`, unused by these Docs pages — they render through
        // `queryCollection`/`<ContentRenderer>`) only ever reads `src`, a module path, and
        // otherwise tries to import the plugin key itself as a bare package name. Passing
        // both keeps real parsing working and avoids that resolution warning in dev.
        remarkPlugins: {
          'docs-links': {
            instance: remarkDocLinks,
            src: fileURLToPath(new URL('./content-plugins/remarkDocLinks.default.ts', import.meta.url)),
            options: { repoBlobUrl: 'https://github.com/TransitTrack/lens/blob/main/' },
          },
        },
        rehypePlugins: {
          // explicit empty `options` — otherwise the codegen (`plugin.options || plugin`)
          // stringifies the whole plugin object, leaking `src` in as the call argument
          'docs-mermaid': {
            instance: rehypeMermaid,
            src: fileURLToPath(new URL('./content-plugins/rehypeMermaid.default.ts', import.meta.url)),
            options: {},
          },
        },
      },
    },
  },
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
      include: [
        '@apollo/client/core',
        '@indoorequal/vue-maplibre-gl',
        '@vue/apollo-composable',
      ]
    },
  },
  nitro: {
    devProxy: {
      '/graphql': {
        target: process.env.NUXT_BACKEND_URL ?? 'http://localhost:8080/graphql',
        changeOrigin: true,
      },
      // Read-only Spring Boot Actuator `configprops` for the Docs → Live configuration page.
      '/actuator/configprops': {
        target:
          process.env.NUXT_BACKEND_ACTUATOR_URL ?? 'http://localhost:8080/actuator/configprops',
        changeOrigin: true,
      },
    },
  },
})
