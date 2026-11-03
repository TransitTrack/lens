// Harness used: the real @nuxtjs/i18n module. The `nuxt` vitest environment boots the
// Nuxt app with all modules, so `useI18n()` / `$t` work unchanged (no plain vue-i18n
// fallback plugin needed). We only pin the locale to `en` so existing English-asserting
// tests are immune to cookie/browser-language detection and to tests that switch locale.
import { beforeEach } from 'vitest'
import { tryUseNuxtApp } from '#imports'

beforeEach(async () => {
  // Some suites run without an active Nuxt context; nothing to reset there.
  const i18n = tryUseNuxtApp()?.$i18n
  if (i18n && i18n.locale.value !== 'en') await i18n.setLocale('en')
})
