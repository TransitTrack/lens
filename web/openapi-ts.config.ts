import { defineConfig } from '@hey-api/openapi-ts'

// Generates the typed API client from the REST contract. Output is committed so
// the app builds without running the generator; refresh it with `pnpm api:generate`.
export default defineConfig({
  input: '../api-contract/openapi.yaml',
  output: { path: 'generated/api', clean: true },
  plugins: [
    '@hey-api/typescript',
    // Relative, so the same build works behind the dev proxy and the nginx `/api` proxy (the
    // contract's `servers` entry is the local backend, for API tools only).
    { name: '@hey-api/client-fetch', throwOnError: true, baseUrl: '/api/v1' },
    { name: '@hey-api/sdk', operations: { strategy: 'byTags' } },
  ],
})
