import { fileURLToPath } from 'node:url'
import { defineCollection, defineContentConfig } from '@nuxt/content'

export default defineContentConfig({
  collections: {
    // The repo-root docs/*.md, rendered as-is under /:feedCode/docs. Only the top
    // level is included — docs/superpowers/ holds internal specs and plans.
    // The web Docker build mounts them via a named build context (see Dockerfile).
    docs: defineCollection({
      type: 'page',
      source: {
        cwd: process.env.NUXT_DOCS_DIR ?? fileURLToPath(new URL('../docs', import.meta.url)),
        include: '*.md',
      },
    }),
  },
})
