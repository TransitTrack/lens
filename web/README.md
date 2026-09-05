# transittrack vehicle dashboard (web)

Nuxt 3/4 + Vue 3 + NuxtUI single-page vehicle dashboard. Standalone frontend project inside the `transittrack` repo, independent of the Kotlin backend.

This is an initial scaffold stub — expanded with real setup/usage docs in a later task.

## Development

```bash
npm install
npm run dev
```

The dev server proxies `/graphql` to the backend GraphQL endpoint (default `http://localhost:8080/graphql`, overridable via `NUXT_BACKEND_URL`).

## Testing

```bash
npm run test
```
