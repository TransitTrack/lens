import { describe, expect, it } from 'vitest'
import type { Root as HastRoot } from 'hast'
import type { Root as MdastRoot } from 'mdast'
import {
  mdcHeadingId,
  rehypeMermaid,
  remarkDocLinks,
  rewriteDocHref,
} from '../../content-plugins/docsMarkdown'

const BLOB = 'https://github.com/org/repo/blob/main/'

describe('mdcHeadingId', () => {
  it('normalises GitHub slugs the way MDC does for heading ids', () => {
    expect(mdcHeadingId('3-avl_feed-table--avlfeedconfigsynchronizer')).toBe(
      '_3-avl_feed-table-avlfeedconfigsynchronizer',
    )
    expect(mdcHeadingId('retention')).toBe('retention')
  })
})

describe('rewriteDocHref', () => {
  it('turns sibling docs into relative routes with MDC anchors', () => {
    expect(rewriteDocHref('avl.md', BLOB)).toBe('avl')
    expect(rewriteDocHref('avl.md#3-avl_feed-table--avlfeedconfigsynchronizer', BLOB)).toBe(
      'avl#_3-avl_feed-table-avlfeedconfigsynchronizer',
    )
  })

  it('rewrites same-page anchors', () => {
    expect(rewriteDocHref('#8-assumptions--deferred', BLOB)).toBe('#_8-assumptions-deferred')
  })

  it('points links leaving docs/ at the repo, keeping the anchor', () => {
    expect(rewriteDocHref('../web/README.md', BLOB)).toBe(`${BLOB}web/README.md`)
    expect(rewriteDocHref('../README.md#monitoring', BLOB)).toBe(`${BLOB}README.md#monitoring`)
    expect(rewriteDocHref('../examples/example-extension/', BLOB)).toBe(
      `${BLOB}examples/example-extension/`,
    )
  })

  it('leaves absolute URLs alone', () => {
    expect(rewriteDocHref('https://gtfs.org', BLOB)).toBe('https://gtfs.org')
    expect(rewriteDocHref('mailto:a@b.c', BLOB)).toBe('mailto:a@b.c')
    expect(rewriteDocHref('/graphiql', BLOB)).toBe('/graphiql')
  })
})

describe('remarkDocLinks', () => {
  it('rewrites inline links and reference definitions', () => {
    const tree: MdastRoot = {
      type: 'root',
      children: [
        {
          type: 'paragraph',
          children: [{ type: 'link', url: 'gtfs.md#1-overview', children: [] }],
        },
        { type: 'definition', identifier: 'ci', url: 'ci.md' },
      ],
    }
    remarkDocLinks({ repoBlobUrl: BLOB })(tree)
    const [paragraph, definition] = tree.children as [
      { children: [{ url: string }] },
      { url: string },
    ]
    expect(paragraph.children[0].url).toBe('gtfs#_1-overview')
    expect(definition.url).toBe('ci')
  })
})

describe('rehypeMermaid', () => {
  it('swaps mermaid fences for <docs-mermaid> and leaves other code alone', () => {
    const pre = (language: string, code: string) => ({
      type: 'element' as const,
      tagName: 'pre',
      properties: { language, code },
      children: [],
    })
    const tree: HastRoot = {
      type: 'root',
      children: [pre('mermaid', 'flowchart LR\n  A --> B'), pre('yaml', 'a: 1')],
    }
    rehypeMermaid()(tree)
    expect(tree.children).toEqual([
      {
        type: 'element',
        tagName: 'docs-mermaid',
        properties: { code: 'flowchart LR\n  A --> B' },
        children: [],
      },
      pre('yaml', 'a: 1'),
    ])
  })
})
