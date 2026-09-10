import { useAgenciesDetailQuery, type AgenciesDetailQuery } from '../../generated/graphql'
import { useFeeds } from './useFeeds'
import { useExploreQuery } from './useExploreQuery'

export type Agency = AgenciesDetailQuery['agencies'][number]

/**
 * Agency selection for the Explore section, persisted in the `?agency=` query
 * param so it survives navigation between explore pages and is shareable.
 */
export function useAgencyFilter() {
  const { get, set } = useExploreQuery()
  const { selectedFeedCode } = useFeeds()

  const { result } = useAgenciesDetailQuery(
    () => ({ feedCode: selectedFeedCode.value ?? '' }),
    () => ({ enabled: !!selectedFeedCode.value }),
  )

  const agencies = computed<Agency[]>(() =>
    [...(result.value?.agencies ?? [])].sort((a, b) =>
      (a.agencyName ?? a.agencyId ?? '').localeCompare(b.agencyName ?? b.agencyId ?? ''),
    ),
  )

  const hasMultiple = computed(() => agencies.value.length > 1)

  const agencyId = computed<string | null>(() => get('agency'))

  const selectedAgency = computed(
    () => agencies.value.find((a) => a.agencyId === agencyId.value) ?? null,
  )

  function setAgency(id: string | null) {
    set('agency', id)
  }

  return { agencies, hasMultiple, agencyId, selectedAgency, setAgency }
}
