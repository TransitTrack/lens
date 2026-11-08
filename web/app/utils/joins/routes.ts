import type { Agency, Route } from '~/api'

export function indexBy<T, K extends keyof T>(rows: T[], key: K): Map<T[K], T> {
  return new Map(rows.map((row) => [row[key], row]))
}

/** A row with its route (or null when the route is unknown). */
export function withRoute<T extends { routeId: string }>(row: T, routes: Map<string, Route>): T & { route: Route | null } {
  return { ...row, route: routes.get(row.routeId) ?? null }
}

export function routesWithAgency(routes: Route[], agencies: Agency[]): Array<Route & { agency: Agency | null }> {
  const byId = indexBy(agencies.filter((a) => a.agencyId != null), 'agencyId')
  return routes.map((route) => ({ ...route, agency: (route.agencyId != null ? byId.get(route.agencyId) : undefined) ?? null }))
}
