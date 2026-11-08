import { defineConfig } from '@hey-api/openapi-ts'

// Generates the typed API client from the REST contract. Output is committed so
// the app builds without running the generator; refresh it with `pnpm api:generate`.
export default defineConfig({
  input: '../api-contract/openapi.yaml',
  output: { path: 'generated/api', clean: true },
  plugins: [
    '@hey-api/typescript',
    '@tanstack/vue-query',
    { name: '@hey-api/client-fetch', throwOnError: true },
    { name: '@hey-api/sdk', operations: { strategy: 'byTags' } },
  ],
})
