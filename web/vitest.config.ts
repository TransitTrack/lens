import { fileURLToPath } from 'node:url'
import { defineVitestConfig } from '@nuxt/test-utils/config'

export default defineVitestConfig({
  test: {
    environment: 'nuxt',
    exclude: ['**/node_modules/**', '**/tests/e2e/**'],
    // happy-dom has no 2d canvas context; VehicleMap.vue pre-renders marker
    // icons to canvas, so stub it. See tests/mocks/canvas.ts.
    setupFiles: ['./tests/mocks/canvas.ts'],
  },
  resolve: {
    alias: {
      // nuxt-maplibre boots a real WebGL map that happy-dom can't run; swap the
      // wrapper package for inert stubs. See tests/mocks/vue-maplibre-gl.ts.
      '@indoorequal/vue-maplibre-gl': fileURLToPath(
        new URL('./tests/mocks/vue-maplibre-gl.ts', import.meta.url),
      ),
    },
  },
})
