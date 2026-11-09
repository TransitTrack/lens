// @ts-check
import vueI18n from '@intlify/eslint-plugin-vue-i18n'
import withNuxt from './.nuxt/eslint.config.mjs'

const generatedHelperPatterns = [{
  group: ['~~/generated/api/@pinia/*', '~~/generated/api/@tanstack/*'],
  message: 'Use the SDK through app/composables/api queries and mutations instead.',
}]

export default withNuxt(
  {
    // Generated from OpenAPI — checked in, not hand-written; not our style rules to enforce.
    ignores: ['generated/**'],
  },
  ...vueI18n.configs['flat/base'],
  {
    settings: {
      'vue-i18n': {
        localeDir: 'i18n/locales/*.json',
        messageSyntaxVersion: '^11.0.0',
      },
    },
  },
  {
    files: ['app/**/*.vue', 'app/**/*.ts'],
    rules: {
      // Catches $t('typo.key') / t('typo.key') with literal keys. Dynamic keys (t(variable)) are not
      // analysable and are skipped by the rule; they are declared via `i18n-keys:` markers and
      // verified by `pnpm i18n:check` instead.
      '@intlify/vue-i18n/no-missing-keys': 'error',
    },
  },
  {
    // Generated Colada / TanStack helpers are replaced by the hand-written queries in app/composables/api.
    files: ['app/**/*.{ts,vue}'],
    rules: {
      'no-restricted-imports': ['error', {
        patterns: generatedHelperPatterns,
      }],
    },
  },
  {
    // Pinia Colada is called from pages, layouts and app/composables/api only (see the
    // pinia-colada-queries design).
    files: ['app/components/**/*.{ts,vue}', 'app/composables/**/*.ts', 'app/utils/**/*.ts', 'app/plugins/**/*.ts'],
    ignores: ['app/composables/api/**'],
    rules: {
      'no-restricted-imports': ['error', {
        paths: [{
          name: '@pinia/colada',
          importNames: ['useQuery', 'useMutation', 'useQueryCache'],
          message: 'Call queries and mutations from pages, layouts or app/composables/api only.',
        }],
        patterns: generatedHelperPatterns,
      }],
    },
  },
  {
    files: ['app/**/*.vue'],
    ignores: ['app/pages/*/docs/**', 'app/components/docs/**', 'app/components/content/**'],
    rules: {
      '@intlify/vue-i18n/no-raw-text': [
        'error',
        {
          // Symbol/punctuation/number-only text (arrows, emoji icons, separators) carries no words to translate.
          ignorePattern: '^[-\\s\\d.,:;/\\\\|·•±—–#%()[\\]{}+*=<>&@!?"\'`~_^$°→⏱🔒]+$',
          // Locale-invariant tokens that legitimately appear as bare template text:
          //   v  = version prefix before a number ("v3"), km / s = SI unit suffixes after an interpolated number,
          //   feed_info.txt = GTFS file name (identifier).
          ignoreText: ['v', 'km', 's', 'feed_info.txt'],
        },
      ],
    },
  },
)
