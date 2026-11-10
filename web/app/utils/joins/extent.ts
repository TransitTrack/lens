import type { Agency } from '~/api'

export interface LngLatBoundsExtent {
  minLon: number
  minLat: number
  maxLon: number
  maxLat: number
}

/** Combines every agency's extent into a single bounding box the map can fit to. */
export function agenciesExtent(agencies: Agency[]): LngLatBoundsExtent | null {
  const extents = agencies.map((a) => a.extent).filter((e) => e != null)
  if (extents.length === 0) return null
  return {
    minLon: Math.min(...extents.map((e) => e.minLon)),
    minLat: Math.min(...extents.map((e) => e.minLat)),
    maxLon: Math.max(...extents.map((e) => e.maxLon)),
    maxLat: Math.max(...extents.map((e) => e.maxLat)),
  }
}
