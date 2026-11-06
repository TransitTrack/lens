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
npm run codegen   # regenerate generated/graphql.ts from ../app/src/main/resources/graphql/*.graphqls
npm run dev       # starts on :3000, proxies /graphql to :8080 (override via NUXT_BACKEND_URL)
```

This is a client-side-only app (`ssr: false` in `nuxt.config.ts`) — there is no
server-rendered data-fetching path, by design (see the spec's architecture
section). All GraphQL queries run in the browser via Apollo Client, polling on
an interval rather than subscribing.

## Code layout

Components under `app/components/` are grouped by domain and always imported by explicit
path (`import AppPage from '~/components/shell/AppPage.vue'`), never via Nuxt auto-import,
so moving a file breaks loudly at typecheck instead of silently at runtime.

- `shell/`: App chrome and page scaffolding: `AppPage`, `PageState`, `PollControl`, notifications, user menu, feed switcher
- `charts/`: Generic `BarChart` / `SparklineChart`
- `map/`: Reusable MapLibre pieces: map toolbox and layers, feed network layer, `VehicleRouteMap`
- `explore/`: Static GTFS explorer: toolbar and filters, stops map, block timeline, feed and validation panels
- `vehicles/`: Live vehicle operations: board, live map, list, filters, detail card, telemetry, AVL log
- `compare/`, `draft/`, `optimize/`, `docs/`: Feature screens of the same name
- `content/`: Global components rendered from Markdown by `@nuxt/content` (e.g. `<docs-mermaid>`)

Composables (`app/composables/`) hold reactive state and data fetching; `app/utils/` holds
pure, testable functions that take `t` / the locale as arguments.

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

The dashboard is translated with `@nuxtjs/i18n` (en, pl, fr, ro; messages in `i18n/locales/*.json`,
glossary in `i18n/glossary.json`). Conventions:

- **Keys** are `area.thing` (`nav.vehicles`, `userMenu.signOut`, `notifications.title`), grouped
  by area, with the three locale files kept structurally identical. Cross-cutting words live in
  `common.*`. English text must stay exactly as written in the source before translation.
- **Use** `$t('area.key')` in templates and `const { t } = useI18n()` in `<script setup>` and
  composables. Never concatenate sentences: use `{name}` / `{count}` parameters.
- **Plurals** use vue-i18n pipes: `"{count} issue | {count} issues"`, called as
  `t(key, { count: n }, n)`. Polish and Romanian need three forms (`one | few | many` and
  `one | few | other`); the rules live in `i18n/pluralRules.ts` (pl: 1 / 2-4 except 12-14 / rest;
  ro: 1 / 0 or n % 100 in 2-19 / rest, and the `other` form takes the "de" preposition, e.g.
  `1 vehicul | 2 vehicule | 20 de vehicule`; fr: 0 and 1 are singular for 2-form messages).
- **Utils** that build display strings must not import global i18n state; they take an injected
  `t: (key: string, params?: Record<string, unknown>) => string` argument and the caller passes `t`.
- **Dynamic keys** (`t(`ns.${value}`)`) are invisible to the checker: declare them with a marker
  comment, `// i18n-keys: ns.status.*` in scripts or `<!-- i18n-keys: ns.* -->` in templates.
  Map API enum/status values through keys with a fallback to the raw value when unknown.
- **Dates, numbers, relative time**: utils take the app locale as an argument and map it with
  `intlLocale()` / `displayLocale()` (`app/utils/intlLocale.ts`; English maps to `en-GB` to keep 24h,
  day-first output). Use `formatNumber(n, locale)` instead of `toLocaleString()` and the
  `useRelativeTime()` composable instead of vueuse `formatTimeAgo` (messages live under `timeAgo.*`).
  Machine formats (ISO `en-CA` dates for the API) stay hardcoded with a comment.
- **Leave alone**: docs pages, GTFS/API free text (names, headsigns), identifiers, CSS, routes.
- **Lint**: `pnpm lint` enforces `@intlify/vue-i18n/no-raw-text` (no untranslated text in templates
  outside the docs areas; the small ignore list is in `eslint.config.mjs`) and `no-missing-keys`.
  CI runs both `pnpm lint` and `pnpm i18n:check --strict` in the Lint workflow, and
  `tests/unit/i18nLocales.spec.ts` checks key/placeholder/plural-form parity and untranslated values.
- **Check** with `pnpm i18n:check` (add `--strict` to fail on warnings): reports missing/extra keys
  across locales, unused keys and keys used in code but not defined. `pnpm i18n:translate` can
  draft translations for new keys.

## Regenerating types

Run `npm run codegen` whenever a `.graphql` operation file under
`app/graphql/` changes, or whenever the backend's
`app/src/main/resources/graphql/*.graphqls` schema changes. The output
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
4. Add the language to the `INTL_TAGS` map in `app/utils/intlLocale.ts`
   (e.g. `de: 'de-DE'`); without it dates, weekdays and numbers silently
   fall back to `en-GB`.
5. If the language has more than two plural forms (cs, ru, uk, ...), add a
   rule for it in `i18n/pluralRules.ts` (`pl` and `ro` are the examples with a
   3-form rule; give 2-form messages a graceful fallback) and cover it in a unit test.
   The translate script only copies the English two-form plurals, so adjust the
   `a | b | c` forms by hand.
6. Optionally add the matching Nuxt UI locale in `app/app.vue` (import it
   next to `en, pl, fr, ro` from `@nuxt/ui/locale` and add it to the `uiLocales`
   map) so built-in component strings are translated too; unknown codes
   fall back to `en`.

7. Add the new code to the `setLocale` cast in `app/composables/useLocaleSwitcher.ts`,
   add an entry for each term in `i18n/glossary.json`, and extend the locale tests
   (`tests/unit/i18nLocales.spec.ts`, `i18nFormatting.spec.ts`, `useLocaleSwitcher.spec.ts`).
   The language picker lists the configured `i18n.locales`, so it needs no change.

Romanian (`ro`) was added exactly this way, but translated by hand instead of with
`pnpm i18n:translate`: locale entry, `INTL_TAGS` (`ro: 'ro-RO'`), a 3-form plural rule
(1 / 0 or 2-19 / rest, with "de" in the `other` form), the Nuxt UI `ro` locale, glossary
entries, `ro.json`, and tests (including `tests/unit/i18nRomanian.spec.ts` for plural forms).
Use comma-below diacritics (ș, ț), not cedilla (ş, ţ).

Run `pnpm i18n:check` (add `--strict` to fail on missing translations) to
verify locale consistency: keys used in code exist in `en`, `en` keys are
used, and `{placeholders}` match across locales. Dynamic keys are not
detected; declare them with a `// i18n-keys: a.b.*` comment (or
`<!-- i18n-keys: a.b.* -->` in templates).
