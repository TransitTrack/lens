// @nuxtjs/mdc's module codegen (processUnistPlugins in its module.mjs) only ever writes
// `import X from '<plugin.src>'` — it reads `src`, not a live function passed as `instance` in
// nuxt.config.ts. A `src` module must therefore default-export the plugin factory itself.
export { remarkDocLinks as default } from './docsMarkdown'
