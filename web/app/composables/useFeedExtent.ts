import { useAgenciesQuery } from '~/composables/useRestGtfs'
import { agenciesExtent } from '~/utils/joins/extent'
import type { LngLatBoundsExtent } from '~/utils/joins/extent'

export type { LngLatBoundsExtent }

/**
 * Combines every agency's extent for the given GTFS feed into a single bounding
 * box the map can fit to.
 */
export function useFeedExtent(gtfsFeedCode: Ref<string | null>) {
  const { result: agenciesResult } = useAgenciesQuery(() => ({
    variables: { feedCode: gtfsFeedCode.value ?? '' },
    enabled: !!gtfsFeedCode.value,
  }))

  const extent = computed<LngLatBoundsExtent | null>(() => agenciesExtent(agenciesResult.value?.agencies ?? []))

  return { extent }
}
