import { routeTypeLabel } from '~/utils/gtfs'

export interface DerivedFeature {
  key: string
  label: string
  present: boolean
}

interface RouteLike {
  routeType?: number | null
  routeColor?: string | null
}

interface FeatureInput {
  filesPresent: string[] | null | undefined
  rowCounts: Record<string, number> | null | undefined
  routes: RouteLike[]
}

/**
 * Derive a MobilityData-style "Features" list from what a revision actually
 * shipped: the files it contained, its row counts, and the parsed route rows.
 * Only signals we can read confidently are reported — anything ambiguous is
 * left off rather than guessed.
 */
export function deriveFeatures(input: FeatureInput): DerivedFeature[] {
  const files = new Set(input.filesPresent ?? [])
  const has = (name: string) => files.has(name)
  const anyFile = (...names: string[]) => names.some((n) => files.has(n))
  const routes = input.routes ?? []

  return [
    { key: 'colors', label: 'Route colors', present: routes.some((r) => !!r.routeColor) },
    { key: 'shapes', label: 'Shapes', present: has('shapes.txt') },
    {
      key: 'calendar',
      label: 'Service calendar',
      present: anyFile('calendar.txt', 'calendar_dates.txt'),
    },
    { key: 'frequencies', label: 'Frequency-based trips', present: has('frequencies.txt') },
    { key: 'transfers', label: 'Transfers', present: has('transfers.txt') },
    { key: 'pathways', label: 'Pathways', present: has('pathways.txt') },
    { key: 'levels', label: 'Levels', present: has('levels.txt') },
    {
      key: 'fares',
      label: 'Fare information',
      present: anyFile(
        'fare_attributes.txt',
        'fare_rules.txt',
        'fare_products.txt',
        'fare_leg_rules.txt',
        'fare_leg_join_rules.txt',
        'fare_transfer_rules.txt',
        'fare_media.txt',
      ),
    },
    { key: 'translations', label: 'Translations', present: has('translations.txt') },
    { key: 'feedinfo', label: 'Feed info', present: has('feed_info.txt') },
    { key: 'attributions', label: 'Attributions', present: has('attributions.txt') },
  ]
}

export interface RouteTypeTally {
  type: number
  label: string
  count: number
}

/** Count routes per GTFS route_type, ordered most common first. */
export function routeTypeBreakdown(routes: RouteLike[]): RouteTypeTally[] {
  const counts = new Map<number, number>()
  for (const r of routes ?? []) {
    if (r.routeType == null) continue
    counts.set(r.routeType, (counts.get(r.routeType) ?? 0) + 1)
  }
  return [...counts.entries()]
    .map(([type, count]) => ({ type, label: routeTypeLabel(type), count }))
    .sort((a, b) => b.count - a.count || a.type - b.type)
}
