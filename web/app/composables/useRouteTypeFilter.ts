import type { Route } from '~/api'
import { useExploreQuery } from './useExploreQuery'
import { routeTypeBreakdown } from '~/utils/feedFeatures'

/**
 * Route-type selection for the Routes list, in `?type=<gtfs route_type>`.
 * Only types actually present in the feed are offered. The routes are fetched
 * by the page and passed in.
 */
export function useRouteTypeFilter(routes: Ref<Route[] | undefined>) {
  const { t } = useI18n()
  const { get, set } = useExploreQuery()

  const types = computed(() => routeTypeBreakdown(routes.value ?? [], t))
  const hasMultiple = computed(() => types.value.length > 1)

  const typeId = computed<string | null>(() => get('type'))

  function setType(value: string | null) {
    set('type', value)
  }

  return { types, hasMultiple, typeId, setType }
}
