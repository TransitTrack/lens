import type { LngLatBoundsExtent } from '~/composables/useFeedExtent'

/**
 * Convert the GraphQL `Extent` shape (as combined by `useFeedExtent`) into the
 * `[[west, south], [east, north]]` tuple maplibre / `<MglMap :bounds>` expects.
 * Returns `undefined` when there is no extent, so it can be bound directly.
 */
export function extentToBounds(
  extent: LngLatBoundsExtent | null | undefined,
): [[number, number], [number, number]] | undefined {
  if (!extent) return undefined
  return [
    [extent.minLon, extent.minLat],
    [extent.maxLon, extent.maxLat],
  ]
}
