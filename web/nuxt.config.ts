// https://nuxt.com/docs/api/configuration/nuxt-config
import {createRequire} from 'node:module'

// @indoorequal/vue-maplibre-gl is nested under nuxt-maplibre/node_modules (not
// hoisted), so resolve its ESM build relative to nuxt-maplibre itself.
const require = createRequire(import.meta.url)
const vueMaplibreEsm = createRequire(require.resolve('nuxt-maplibre')).resolve(
  '@indoorequal/vue-maplibre-gl/dist/vue-maplibre-gl.es.js',
)

export default defineNuxtConfig({
  compatibilityDate: '2026-09-06',
  // This dashboard is a client-side-polling ops UI (see spec: no SSR data-fetching
  // is needed or wanted). Disabling SSR also sidesteps a real crash class where
  // universal code (e.g. the Apollo plugin) runs during server rendering with no
  // browser origin to resolve relative GraphQL URIs against.
  ssr: false,
  modules: ['@nuxt/ui', '@nuxt/eslint', 'nuxt-maplibre'],
  css: ['~/assets/css/main.css'],
  devtools: {enabled: true},
  vite: {
    resolve: {
      alias: {
        // nuxt-maplibre resolves @indoorequal/vue-maplibre-gl to its CJS/UMD
        // build (package.json `main`), whose named exports Vite's interop can't
        // statically see — the browser then errors with "does not provide an
        // export named 'MglNavigationControl'". Pin it to the absolute path of
        // the ESM build (absolute, so prefix-matching doesn't re-alias it).
        '@indoorequal/vue-maplibre-gl': vueMaplibreEsm,
      },
    },
    // vue-maplibre-gl and maplibre-gl both ship non-ESM builds; let Vite's dep
    // optimizer pre-bundle them so their named exports (AttributionControl,
    // MglNavigationControl, …) resolve in the browser. Pre-bundling maplibre-gl
    // emits a harmless "?worker file does not exist in optimize deps" warning
    // on dev boot.
    optimizeDeps: {include: ['@indoorequal/vue-maplibre-gl', 'maplibre-gl']},
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
