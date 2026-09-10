import { patternLine, hexColor, routeTypeLabel } from '~/utils/gtfs'
import { useFeedGeometryQuery } from '~~/generated/graphql'

/** Feature properties carried on each route polyline — read by hover handlers. */
export interface RouteLineProps {
  routeId: string
  color: string
  shortName: string
  longName: string
  typeLabel: string
  headsign: string
  tripCount: number
}

/**
 * The GTFS network geometry for a feed — route pattern polylines (coloured by
 * route_color) and boarding stops — as ready-to-render GeoJSON. One cached fetch;
 * safe to mount behind a live-polling map as a static base layer.
 */
export function useFeedGeometry(gtfsFeedCode: Ref<string | null>) {
  const { result, loading } = useFeedGeometryQuery(
    () => ({ feedCode: gtfsFeedCode.value ?? '' }),
    () => ({ enabled: !!gtfsFeedCode.value }),
  )

  const lines = computed<GeoJSON.FeatureCollection>(() => ({
    type: 'FeatureCollection',
    features: (result.value?.tripPatterns ?? [])
      .map((p) => {
        const coords = patternLine(p.stopPaths)
        if (coords.length < 2) return null
        const props: RouteLineProps = {
          routeId: p.routeId,
          color: hexColor(p.route?.routeColor),
          shortName: p.route?.routeShortName ?? '',
          longName: p.route?.routeLongName ?? '',
          typeLabel: routeTypeLabel(p.route?.routeType),
          headsign: p.headsign ?? '',
          tripCount: p.tripCount,
        }
        return {
          type: 'Feature' as const,
          properties: props,
          geometry: { type: 'LineString' as const, coordinates: coords },
        }
      })
      .filter((f): f is GeoJSON.Feature => f != null),
  }))

  const stops = computed<GeoJSON.FeatureCollection>(() => ({
    type: 'FeatureCollection',
    features: (result.value?.stops ?? [])
      .filter((s) => s.stopLat != null && s.stopLon != null && (s.locationType ?? 0) === 0)
      .map((s) => ({
        type: 'Feature' as const,
        properties: { name: s.stopName ?? s.stopId },
        geometry: { type: 'Point' as const, coordinates: [s.stopLon as number, s.stopLat as number] },
      })),
  }))

  return { lines, stops, loading }
}
