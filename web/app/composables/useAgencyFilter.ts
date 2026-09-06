import { useAgenciesDetailQuery, type AgenciesDetailQuery } from '../../generated/graphql'
import { useFeeds } from './useFeeds'

export type Agency = AgenciesDetailQuery['agencies'][number]

/**
 * Agency selection for the Explore section, persisted in the `?agency=` query
 * param so it survives navigation between explore pages and is shareable.
 */
export function useAgencyFilter() {
  const route = useRoute()
  const router = useRouter()
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

  const agencyId = computed<string | null>(() => {
    const q = route.query.agency
    return typeof q === 'string' && q ? q : null
  })

  const selectedAgency = computed(
    () => agencies.value.find((a) => a.agencyId === agencyId.value) ?? null,
  )

  function setAgency(id: string | null) {
    const query = { ...route.query }
    if (id) query.agency = id
    else delete query.agency
    router.replace({ query })
  }

  return { agencies, hasMultiple, agencyId, selectedAgency, setAgency }
}
