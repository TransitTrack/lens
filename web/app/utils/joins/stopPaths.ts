import type { Stop, StopPath } from '~/api'

export type StopPathWithStop = StopPath & { stop: Stop | null }

/** Stop paths grouped by pattern key, each carrying its stop (null when the stop is missing). */
export function groupStopPaths(paths: StopPath[], stops: Stop[] | Map<string, Stop>): Map<string, StopPathWithStop[]> {
  const byId = stops instanceof Map ? stops : new Map(stops.map((stop) => [stop.stopId, stop]))
  const grouped = new Map<string, StopPathWithStop[]>()
  for (const path of paths) {
    const list = grouped.get(path.patternKey) ?? []
    list.push({ ...path, stop: byId.get(path.stopId) ?? null })
    grouped.set(path.patternKey, list)
  }
  return grouped
}

export function patternsWithStopPaths<P extends { patternKey: string }>(
  patterns: P[],
  grouped: Map<string, StopPathWithStop[]>,
): Array<P & { stopPaths: StopPathWithStop[] }> {
  return patterns.map((pattern) => ({ ...pattern, stopPaths: grouped.get(pattern.patternKey) ?? [] }))
}
