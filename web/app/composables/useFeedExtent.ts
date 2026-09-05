import { useAvlFeedsQuery, useAgenciesQuery } from '../../generated/graphql'

export interface LngLatBoundsExtent {
  minLon: number
  minLat: number
  maxLon: number
  maxLat: number
}

/**
 * Resolves the selected AVL feed's underlying GTFS feed, then combines every
 * agency's extent into a single bounding box the map can fit to.
 */
export function useFeedExtent(feedCode: Ref<string | null>) {
  const { result: avlFeedsResult } = useAvlFeedsQuery(() => ({ pollInterval: 60_000 }))

  const gtfsFeedCode = computed(
    () =>
      avlFeedsResult.value?.avlFeeds.find((f) => f.code === feedCode.value)?.gtfsFeedCode ?? null,
  )

  const { result: agenciesResult } = useAgenciesQuery(
    () => ({ feedCode: gtfsFeedCode.value ?? '' }),
    () => ({ enabled: !!gtfsFeedCode.value }),
  )

  const extent = computed<LngLatBoundsExtent | null>(() => {
    const agencies = agenciesResult.value?.agencies ?? []
    const extents = agencies.map((a) => a.extent).filter((e) => e != null)
    if (extents.length === 0) return null
    return {
      minLon: Math.min(...extents.map((e) => e.minLon)),
      minLat: Math.min(...extents.map((e) => e.minLat)),
      maxLon: Math.max(...extents.map((e) => e.maxLon)),
      maxLat: Math.max(...extents.map((e) => e.maxLat)),
    }
  })

  return { extent }
}
