# Vehicle dashboard

A Nuxt 4 / Vue 3 / NuxtUI live dashboard for AVL-matched vehicles, reading the
`transittrack` backend's GraphQL API.

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

## Translating UI text

The dashboard is translated with `@nuxtjs/i18n` (en, pl, fr; messages in `i18n/locales/*.json`,
glossary in `i18n/glossary.json`). Conventions:

- **Keys** are `area.thing` (`nav.vehicles`, `userMenu.signOut`, `notifications.title`), grouped
  by area, with the three locale files kept structurally identical. Cross-cutting words live in
  `common.*`. English text must stay exactly as written in the source before translation.
- **Use** `$t('area.key')` in templates and `const { t } = useI18n()` in `<script setup>` and
  composables. Never concatenate sentences: use `{name}` / `{count}` parameters.
- **Plurals** use vue-i18n pipes: `"{count} issue | {count} issues"`, called as
  `t(key, { count: n }, n)`. Polish needs three forms (`one | few | many`); the rules live in
  `i18n/pluralRules.ts` (pl: 1 / 2-4 except 12-14 / rest; fr: 0 and 1 are singular for 2-form messages).
- **Utils** that build display strings must not import global i18n state; they take an injected
  `t: (key: string, params?: Record<string, unknown>) => string` argument and the caller passes `t`.
- **Dynamic keys** (`t(`ns.${value}`)`) are invisible to the checker: declare them with a marker
  comment, `// i18n-keys: ns.status.*` in scripts or `<!-- i18n-keys: ns.* -->` in templates.
  Map API enum/status values through keys with a fallback to the raw value when unknown.
- **Leave alone**: docs pages, GTFS/API free text (names, headsigns), identifiers, CSS, routes.
- **Check** with `pnpm i18n:check` (add `--strict` to fail on warnings): reports missing/extra keys
  across locales, unused keys and keys used in code but not defined. `pnpm i18n:translate` can
  draft translations for new keys.

## Regenerating types

Run `npm run codegen` whenever a `.graphql` operation file under
`app/graphql/` changes, or whenever the backend's
`src/main/resources/graphql/*.graphqls` schema changes. The output
(`generated/graphql.ts`) is checked in.

## Known limitations / deferred

See the design spec's §8 — no headway screen, no auth, polling not push
updates, no production build/deploy strategy yet.

## Adding a language

1. Add an entry to `i18n.locales` in `nuxt.config.ts`, e.g.
   `{ code: 'de', language: 'de-DE', name: 'Deutsch', file: 'de.json' }`.
2. Generate the translations from `i18n/locales/en.json`:

   ```bash
   pnpm i18n:translate --locale de --dry-run   # preview the keys to be added
   pnpm i18n:translate --locale de             # write i18n/locales/de.json
   pnpm i18n:translate --locale de --provider deepl   # needs DEEPL_API_KEY
   ```

   Provider precedence: `--provider` flag, then `I18N_PROVIDER` env, then
   `google-free`. Providers: `google-free` (default, no key), `deepl`
   (`DEEPL_API_KEY`; `:fx` keys use the free API, others the Pro API, or set
   `DEEPL_API_URL`),
   `google-cloud` (`GOOGLE_TRANSLATE_API_KEY`). Existing values are never
   overwritten, so the command is safe to rerun after adding new `en` keys.
   Keys whose `{placeholders}` the provider mangled are skipped and reported
   (non-zero exit); fix them by hand or rerun. Domain terms come from
   `i18n/glossary.json`.
3. Review the output: it is machine-quality.
4. Optionally add the matching Nuxt UI locale in `app.vue` so built-in
   component strings are translated too.

Run `pnpm i18n:check` (add `--strict` to fail on missing translations) to
verify locale consistency: keys used in code exist in `en`, `en` keys are
used, and `{placeholders}` match across locales. Dynamic keys are not
detected; declare them with a `// i18n-keys: a.b.*` comment (or
`<!-- i18n-keys: a.b.* -->` in templates).
