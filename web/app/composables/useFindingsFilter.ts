import type { ValidationFindingCount } from '~/api'

/**
 * "Only show entities with analyzer findings" for an explore list. `keep` is the row predicate to
 * AND into the list's own filters; `count` is how many entities of this type have findings, so the
 * switch can say what it would reveal. The switch is hidden when the feed has none. The finding
 * counts are fetched by the page and passed in.
 */
export function useFindingsFilter(counts: Ref<ValidationFindingCount[] | undefined>) {
  const onlyWithFindings = ref(false)

  const ids = computed(() => new Set((counts.value ?? []).map((entry) => entry.entityId)))
  const count = computed(() => ids.value.size)

  function keep(entityId: string): boolean {
    return !onlyWithFindings.value || ids.value.has(entityId)
  }

  return { onlyWithFindings, count, keep }
}
