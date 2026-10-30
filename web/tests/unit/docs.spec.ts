import { describe, expect, it } from 'vitest'
import { docSearchItems, groupDocs } from '../../app/utils/docs'

describe('groupDocs', () => {
  it('orders known docs by reading order and puts unknown ones under More', () => {
    const groups = groupDocs([
      { title: 'Deployment', path: '/deployment' },
      { title: 'Zeta notes', path: '/zeta' },
      { title: 'AVL', path: '/avl' },
      { title: 'GTFS', path: '/gtfs' },
      { title: 'Alpha notes', path: '/alpha' },
    ])
    expect(groups.map((g) => [g.title, g.docs.map((d) => d.path)])).toEqual([
      ['How it works', ['/gtfs', '/avl']],
      ['Running it', ['/deployment']],
      ['More', ['/alpha', '/zeta']],
    ])
  })
})

describe('docSearchItems', () => {
  it('links sections under the feed-scoped docs base', () => {
    const [page, section] = docSearchItems(
      [
        { id: '/avl', title: 'AVL Ingestion', titles: [], content: 'intro' },
        {
          id: '/avl#6-retention',
          title: '6. Retention',
          titles: ['AVL Ingestion'],
          content: 'rows older',
        },
      ],
      '/kombus/docs',
    )
    expect(page).toMatchObject({
      to: '/kombus/docs/avl',
      icon: 'i-lucide-book-open',
      suffix: undefined,
    })
    expect(section).toMatchObject({
      to: '/kombus/docs/avl#6-retention',
      icon: 'i-lucide-hash',
      suffix: 'AVL Ingestion',
      content: 'rows older',
    })
  })
})
