import { useExploreRoutesQuery } from '../../generated/graphql'
import { useFeeds } from './useFeeds'
import { useExploreQuery } from './useExploreQuery'
import { routeTypeBreakdown } from '~/utils/feedFeatures'

/**
 * Route-type selection for the Routes list, in `?type=<gtfs route_type>`.
 * Only types actually present in the feed are offered.
 */
export function useRouteTypeFilter() {
  const { t } = useI18n()
  const { selectedFeedCode } = useFeeds()
  const { get, set } = useExploreQuery()

  const { result } = useExploreRoutesQuery(
    () => ({ feedCode: selectedFeedCode.value ?? '' }),
    () => ({ enabled: !!selectedFeedCode.value }),
  )

  const types = computed(() => routeTypeBreakdown(result.value?.routes ?? [], t))
  const hasMultiple = computed(() => types.value.length > 1)

  const typeId = computed<string | null>(() => get('type'))

  function setType(value: string | null) {
    set('type', value)
  }

  return { types, hasMultiple, typeId, setType }
}
