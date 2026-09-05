# Vehicle dashboard

A Nuxt 4 / Vue 3 / NuxtUI live dashboard for AVL-matched vehicles, reading the
`transittrack` backend's GraphQL API. See
`docs/superpowers/specs/2026-09-06-vehicle-dashboard-ui-design.md` for the
design.

## Prerequisites

- Node.js (LTS)
- The backend running locally with `transittrack.avl.enabled=true` and
  `transittrack.predict.enabled=true`, and at least one AVL feed configured
  and actively polling — otherwise the dashboard has nothing to show.

## Development

```bash
npm install
npm run codegen   # regenerate generated/graphql.ts from ../src/main/resources/graphql/*.graphqls
npm run dev       # starts on :3000, proxies /graphql to :8080 (override via NUXT_BACKEND_URL)
```

This is a client-side-only app (`ssr: false` in `nuxt.config.ts`) — there is no
server-rendered data-fetching path, by design (see the spec's architecture
section). All GraphQL queries run in the browser via Apollo Client, polling on
an interval rather than subscribing.

## Testing

```bash
npm run test       # Vitest: unit + component tests, against MSW-mocked GraphQL
npm run test:e2e   # Playwright: one smoke flow, against a real dev server + network-mocked GraphQL
npm run lint
```

`npm run test:e2e` starts its own dev server (see `playwright.config.ts`) bound
to `0.0.0.0` — some environments only resolve the IPv6 loopback for a plain
`nuxt dev`, which Playwright's readiness probe and browser can't reach over
`127.0.0.1`.

## Regenerating types

Run `npm run codegen` whenever a `.graphql` operation file under
`app/graphql/` changes, or whenever the backend's
`src/main/resources/graphql/*.graphqls` schema changes. The output
(`generated/graphql.ts`) is checked in.

## Known limitations / deferred

See the design spec's §8 — no headway screen, no auth, polling not push
updates, no production build/deploy strategy yet.
