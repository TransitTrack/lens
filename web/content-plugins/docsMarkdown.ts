import { toString } from 'hast-util-to-string'
import { visit } from 'unist-util-visit'
import type { Root as HastRoot } from 'hast'
import type { Root as MdastRoot } from 'mdast'

/*
 * Markdown plugins for the `docs` content collection (the repo's docs/*.md),
 * registered in nuxt.config.ts.
 */

export interface DocLinkOptions {
  /**
   * Base URL that repo-relative links leaving `docs/` (e.g. `../web/README.md`)
   * resolve against, e.g. `https://github.com/org/repo/blob/main/`.
   */
  repoBlobUrl: string
}

const DOC_LINK = /^([\w-]+)\.md(?:#(.*))?$/

/**
 * The docs' anchors are GitHub heading slugs (`3-avl_feed-table--avl...`).
 * MDC slugs headings the same way and then normalises the result (collapsed
 * dashes, `_` before a leading digit), so apply that same normalisation to
 * link targets.
 */
export function mdcHeadingId(githubSlug: string): string {
  return githubSlug.replace(/-+/g, '-').replace(/^-|-$/g, '').replace(/^(\d)/, '_$1')
}

/**
 * Rewrites a link found in `docs/<page>.md` so it works inside the app:
 * - sibling docs (`avl.md#3-...`) become relative routes (`avl#_3-...`), which
 *   resolve against the current `/<feed>/docs/<page>` URL;
 * - same-page anchors get the MDC heading id;
 * - links climbing out of `docs/` (`../web/README.md`) point at the repo host;
 * - absolute URLs are left alone.
 */
export function rewriteDocHref(href: string, repoBlobUrl: string): string {
  if (/^[a-z][a-z\d+.-]*:/i.test(href) || href.startsWith('/')) return href
  if (href.startsWith('#')) return `#${mdcHeadingId(href.slice(1))}`
  const doc = DOC_LINK.exec(href)
  if (doc) return doc[2] ? `${doc[1]}#${mdcHeadingId(doc[2])}` : doc[1]!
  const url = new URL(href, 'https://repo.invalid/docs/')
  return repoBlobUrl.replace(/\/?$/, '/') + url.pathname.slice(1) + url.hash
}

/**
 * Remark (not rehype) on purpose: Nuxt Content strips `.md` from links while
 * converting to HTML, after which doc links and repo links look alike.
 */
export function remarkDocLinks(options: DocLinkOptions) {
  return (tree: MdastRoot) => {
    visit(tree, (node) => {
      if (node.type === 'link' || node.type === 'definition') {
        node.url = rewriteDocHref(node.url, options.repoBlobUrl)
      }
    })
  }
}

/** ```mermaid fences become a `<docs-mermaid>` element, rendered by DocsMermaid.vue. */
export function rehypeMermaid() {
  return (tree: HastRoot) => {
    visit(tree, 'element', (node, index, parent) => {
      if (
        node.tagName !== 'pre' ||
        node.properties.language !== 'mermaid' ||
        !parent ||
        index == null
      )
        return
      const code = typeof node.properties.code === 'string' ? node.properties.code : toString(node)
      parent.children[index] = {
        type: 'element',
        tagName: 'docs-mermaid',
        properties: { code },
        children: [],
      }
    })
  }
}
