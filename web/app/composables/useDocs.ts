import { groupDocs, type DocSummary } from '~/utils/docs'
import { useFeeds } from '~/composables/useFeeds'

/**
 * The rendered docs (repo docs/*.md via the `docs` content collection), grouped
 * in reading order. Shared by the Docs index, the docs side navigation and the
 * previous/next links, so the collection is queried once.
 */
export function useDocs() {
  const { feedPath } = useFeeds()
  const { data, status } = useAsyncData('docs-list', () =>
    queryCollection('docs').select('title', 'path', 'description').all(),
  )

  const groups = computed(() => groupDocs((data.value ?? []) as DocSummary[]))
  const ordered = computed(() => groups.value.flatMap((g) => g.docs))

  /** app route for a collection path (`/avl` → `/kombus/docs/avl`) */
  function docRoute(path = ''): string {
    return feedPath(`/docs${path}`)
  }

  return { groups, ordered, status, docRoute }
}
