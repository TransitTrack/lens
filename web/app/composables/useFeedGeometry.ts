import { hexColor, routeTypeLabel } from '~/utils/gtfs'
import { decodeGeoJson } from '~/utils/geojson'
import { useFeedGeometryQuery } from '~~/generated/graphql'
import type { GeoJSON } from 'geojson'

/** Properties the backend puts on each `kind: 'pattern'` feature. */
interface PatternProps {
  routeId: string
  headsign?: string
  tripCount: number
  routeShortName?: string
  routeLongName?: string
  routeType?: number
  routeColor?: string
}

/** Feature properties carried on each route polyline — read by hover handlers. */
export interface RouteLineProps {
  routeId: string
  color: string
  distinctColor: string
  shortName: string
  longName: string
  typeLabel: string
  headsign: string
  tripCount: number
}

const DISTINCT_ROUTE_COLORS = ['#2563eb', '#db2777', '#16a34a', '#d97706', '#7c3aed', '#0891b2']

function distinctRouteColor(routeId: string): string {
  let hash = 0
  for (const character of routeId) hash = (hash * 31 + character.charCodeAt(0)) | 0
  return DISTINCT_ROUTE_COLORS[Math.abs(hash) % DISTINCT_ROUTE_COLORS.length]!
}

/**
 * The GTFS network geometry for a feed — route pattern polylines (coloured by
 * route_color) and boarding stops — as ready-to-render GeoJSON. One cached fetch;
 * safe to mount behind a live-polling map as a static base layer.
 */
export function useFeedGeometry(gtfsFeedCode: Ref<string | null>) {
  const { t } = useI18n()
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

  // Decode once per result; `lines` and `stops` are views over the same collection.
  const all = computed<GeoJSON.Feature[]>(() => {
    const encoded = result.value?.feedGeometry
    return encoded ? decodeGeoJson<GeoJSON.FeatureCollection>(encoded).features : []
  })

  const lines = computed<GeoJSON.FeatureCollection>(() => ({
    type: 'FeatureCollection',
    features: all.value
      .filter((f) => f.properties?.kind === 'pattern')
      .map((f) => {
        const p = f.properties as unknown as PatternProps
        const props: RouteLineProps = {
          routeId: p.routeId,
          color: hexColor(p.routeColor),
          distinctColor: distinctRouteColor(p.routeId),
          shortName: p.routeShortName ?? '',
          longName: p.routeLongName ?? '',
          typeLabel: routeTypeLabel(p.routeType, t),
          headsign: p.headsign ?? '',
          tripCount: p.tripCount,
        }
        return { ...f, properties: props }
      }),
  }))

  const stops = computed<GeoJSON.FeatureCollection>(() => ({
    type: 'FeatureCollection',
    features: all.value
      .filter((f) => f.properties?.kind === 'stop')
      .map((f) => ({ ...f, properties: { name: f.properties?.name } })),
  }))

  return { lines, stops, loading }
}
