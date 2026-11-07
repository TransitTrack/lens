import { useValidationFindingCountsQuery } from '~/composables/useRestDetails'


/**
 * "Only show entities with analyzer findings" for an explore list. `keep` is the row predicate to
 * AND into the list's own filters; `count` is how many entities of this type have findings, so the
 * switch can say what it would reveal. The switch is hidden when the feed has none.
 */
export function useFindingsFilter(
  feedCode: Ref<string | null | undefined>,
  entityType: 'Trip' | 'Stop' | 'Route',
) {
  const onlyWithFindings = ref(false)

  const { result } = useValidationFindingCountsQuery(() => ({
    variables: { feedCode: feedCode.value ?? '', entityType },
    enabled: !!feedCode.value,
  }))

  const ids = computed(
    () => new Set((result.value?.validationFindingCounts ?? []).map((entry) => entry.entityId)),
  )
  const count = computed(() => ids.value.size)

  function keep(entityId: string): boolean {
    return !onlyWithFindings.value || ids.value.has(entityId)
  }

  return { onlyWithFindings, count, keep }
}
