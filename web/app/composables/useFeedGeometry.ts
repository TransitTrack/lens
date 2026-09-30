import { patternLine, simplifyLine, hexColor, routeTypeLabel } from '~/utils/gtfs'
import { useFeedGeometryQuery } from '~~/generated/graphql'
import type { GeoJSON } from 'geojson'

/** ~3m at mid latitudes — well under what's visually distinguishable at the
 * zoom levels this network layer is shown at, but cuts raw GPS-resolution
 * shape points substantially (see `simplifyLine`). */
const LINE_SIMPLIFY_TOLERANCE_DEG = 0.00003

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
    () => ({
      enabled: !!gtfsFeedCode.value,
      // This is the whole agency's trip-pattern + stop geometry — hundreds to
      // thousands of normalized entities. Writing it through the InMemoryCache
      // bloats the store, and every *other* query's write (e.g. the 60s
      // avlFeeds poll) then pays store-wide dependency-tracking/GC overhead
      // proportional to that size — a periodic multi-hundred-ms stall
      // unrelated to what actually changed. Nobody reads this back from the
      // cache, so skip it.
      fetchPolicy: 'no-cache',
    }),
  )

  const lines = computed<GeoJSON.FeatureCollection>(() => ({
    type: 'FeatureCollection',
    features: (result.value?.tripPatterns ?? [])
      .map((p) => {
        const coords = simplifyLine(patternLine(p.stopPaths), LINE_SIMPLIFY_TOLERANCE_DEG)
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
        geometry: {
          type: 'Point' as const,
          coordinates: [s.stopLon as number, s.stopLat as number],
        },
      })),
  }))

  return { lines, stops, loading }
}
