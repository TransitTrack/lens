import type { Agency } from '~/api'
import { useExploreQuery } from './useExploreQuery'

export type { Agency }

/**
 * Agency selection for the Explore section, persisted in the `?agency=` query
 * param so it survives navigation between explore pages and is shareable.
 * The agencies are fetched by the page and passed in.
 */
export function useAgencyFilter(agenciesData: Ref<Agency[] | undefined>) {
  const { get, set } = useExploreQuery()

  const agencies = computed<Agency[]>(() =>
    [...(agenciesData.value ?? [])].sort((a, b) =>
      (a.agencyName ?? a.agencyId ?? '').localeCompare(b.agencyName ?? b.agencyId ?? ''),
    ),
  )

  const hasMultiple = computed(() => agencies.value.length > 1)

  const agencyId = computed<string | null>(() => get('agency'))

  const selectedAgency = computed(
    () => agencies.value.find((a) => a.agencyId === agencyId.value) ?? null,
  )

  function setAgency(id: string | null | undefined) {
    set('agency', id ?? null)
  }

  return { agencies, hasMultiple, agencyId, selectedAgency, setAgency }
}
