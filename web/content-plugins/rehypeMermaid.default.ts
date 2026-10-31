// See remarkDocLinks.default.ts for why this wrapper exists: @nuxtjs/mdc's codegen needs a `src`
// module with a default export, not a function passed directly as `instance`.
export { rehypeMermaid as default } from './docsMarkdown'
