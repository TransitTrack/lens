/**
 * Ordering and linking for the in-app Docs pages, which render the repo's
 * docs/*.md through the `docs` Nuxt Content collection (content.config.ts).
 * Collection paths are `/<file stem>`; in the app they live under
 * `/<feed>/docs/<file stem>`.
 */

export interface DocSummary {
  title: string
  /** collection path, e.g. `/avl` */
  path: string
  description?: string
}

export interface DocGroup {
  title: string
  docs: DocSummary[]
}

/**
 * Reading order: the pipeline docs follow the data (GTFS → schedule model →
 * AVL matching → predictions), then the operator-facing references. A doc not
 * listed here still shows up, under "More".
 */
const DOC_GROUPS: { title: string; stems: string[] }[] = [
  { title: 'How it works', stems: ['gtfs', 'schedule', 'avl', 'predictions'] },
  { title: 'Running it', stems: ['configuration', 'deployment', 'ci', 'benchmarks'] },
]

export function docStem(path: string): string {
  return path.replace(/^\//, '')
}

export function groupDocs(docs: DocSummary[]): DocGroup[] {
  const byStem = new Map(docs.map((d) => [docStem(d.path), d]))
  const groups: DocGroup[] = DOC_GROUPS.map((g) => ({
    title: g.title,
    docs: g.stems.flatMap((s) => {
      const doc = byStem.get(s)
      byStem.delete(s)
      return doc ? [doc] : []
    }),
  }))
  const rest = [...byStem.values()].sort((a, b) => a.title.localeCompare(b.title))
  if (rest.length) groups.push({ title: 'More', docs: rest })
  return groups.filter((g) => g.docs.length > 0)
}

/** A section as returned by `queryCollectionSearchSections('docs')`. */
export interface DocSearchSection {
  /** `/avl` for the page itself, `/avl#3-avl_feed-table--...` for a heading */
  id: string
  title: string
  /** parent heading titles, outermost first (the page title for top-level sections) */
  titles: string[]
  content: string
}

export interface DocSearchItem {
  id: string
  label: string
  suffix?: string
  /** searched (see the layout's fuse keys) but not displayed */
  content: string
  icon: string
  to: string
}

/** Command-palette items for docs sections, linking into `docsBase` (e.g. `/kombus/docs`). */
export function docSearchItems(sections: DocSearchSection[], docsBase: string): DocSearchItem[] {
  return sections.map((s) => ({
    id: `doc-${s.id}`,
    label: s.title,
    suffix: s.titles.length ? s.titles.join(' › ') : undefined,
    content: s.content,
    icon: s.id.includes('#') ? 'i-lucide-hash' : 'i-lucide-book-open',
    to: `${docsBase}${s.id}`,
  }))
}
