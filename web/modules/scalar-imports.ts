import { defineNuxtModule } from '@nuxt/kit'

/**
 * @scalar/nuxt excludes every path matching `/scalar/` from Nuxt's auto-import transform, including its
 * own page and component, which import `useRoute`, `useFetch`, … from `#imports`. Untransformed, that
 * specifier loads the app's whole auto-import barrel — here including @nuxtjs/mdc's markdown parser,
 * whose CommonJS dependencies (`extend`, `debug`, …) break the page in dev under pnpm ("does not
 * provide an export named 'default'").
 *
 * Narrow the exclusion to the other @scalar packages, so the module's own runtime files get their
 * `#imports` rewritten to direct imports like every other Nuxt file.
 */
export default defineNuxtModule({
  meta: { name: 'scalar-imports' },
  setup(_options, nuxt) {
    nuxt.hook('modules:done', () => {
      const exclude = nuxt.options.imports.transform?.exclude
      if (!exclude) return
      const at = exclude.findIndex((pattern) => pattern instanceof RegExp && pattern.source === 'scalar')
      if (at === -1) return
      exclude.splice(at, 1, /[\\/]node_modules[\\/](?:\.pnpm[\\/][^\\/]+[\\/]node_modules[\\/])?@scalar[\\/](?!nuxt[\\/])/)
    })
  },
})
