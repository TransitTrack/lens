import { AvlFeedCatalog, Gtfs, Schedule } from '~/api'
import type {
  AvlFeedsQuery, ExplorePatternsQuery, ExplorePatternsQueryVariables,
  ExploreRoutesQuery, ExploreRoutesQueryVariables, ExploreStopsQuery, ExploreStopsQueryVariables,
  FeedsQuery, TripsByRouteQuery, TripsByRouteQueryVariables,
  ExploreCalendarQuery, ExploreCalendarQueryVariables, AgenciesDetailQuery, AgenciesDetailQueryVariables,
  RoutesQuery, RoutesQueryVariables, StopsQuery, StopsQueryVariables, AgenciesQuery, AgenciesQueryVariables,
} from '~/types/restViews'
import { fetchAllPages } from '~/utils/restPagination'
import { routesById, stopPathsByPattern } from '~/utils/restRelations'
import { useRestQuery, type RestQueryOptions } from './useRestQuery'

async function explorePatterns(feedCode: string, signal: AbortSignal): Promise<ExplorePatternsQuery['tripPatterns']> {
  const [patterns, routes, stopPaths] = await Promise.all([
    fetchAllPages((offset, limit) => Schedule.tripPatterns({ path: { feedCode }, query: { offset, limit }, signal })),
    routesById({ feedCode }, signal),
    stopPathsByPattern({ feedCode }, signal),
  ])
  return patterns.map((p) => ({ ...p, route: routes.get(p.routeId) ?? null, stopPaths: stopPaths.get(p.patternKey) ?? [] })) as ExplorePatternsQuery['tripPatterns']
}

async function feedsWithActiveRevision(signal: AbortSignal): Promise<FeedsQuery['feeds']> {
  const feeds = await fetchAllPages((offset, limit) => Gtfs.feeds({ query: { offset, limit }, signal }))
  return await Promise.all(feeds.map(async (feed) => {
    const [activeRevision] = await fetchAllPages((offset, limit) => Gtfs.revisions({ path: { feedCode: feed.code }, query: { status: 'ACTIVE', offset, limit }, signal }))
    return { ...feed, activeRevision: activeRevision ?? null }
  })) as FeedsQuery['feeds']
}

export function useExploreRoutesQuery(options: () => RestQueryOptions<ExploreRoutesQueryVariables>) {
  return useRestQuery('useExploreRoutesQuery', options, async (variables, signal): Promise<ExploreRoutesQuery> => {
    const [routes, agencies] = await Promise.all([
      fetchAllPages((offset, limit) => Gtfs.routes({ path: { feedCode: variables.feedCode }, query: { offset, limit }, signal })),
      fetchAllPages((offset, limit) => Gtfs.agencies({ path: { feedCode: variables.feedCode }, query: { offset, limit }, signal })),
    ])
    return { routes, agencies } as unknown as ExploreRoutesQuery
  })
}

export function useExploreStopsQuery(options: () => RestQueryOptions<ExploreStopsQueryVariables>) {
  return useRestQuery('useExploreStopsQuery', options, async (variables, signal): Promise<ExploreStopsQuery> => ({
    stops: await fetchAllPages((offset, limit) => Gtfs.stops({ path: { feedCode: variables.feedCode }, query: { offset, limit }, signal })) as ExploreStopsQuery['stops'],
  }))
}

export function useTripsByRouteQuery(options: () => RestQueryOptions<TripsByRouteQueryVariables>) {
  return useRestQuery('useTripsByRouteQuery', options, async (variables, signal): Promise<TripsByRouteQuery> => ({
    trips: await fetchAllPages((offset, limit) => Gtfs.trips({ path: { feedCode: variables.feedCode }, query: { routeId: variables.routeId, offset, limit }, signal })) as TripsByRouteQuery['trips'],
  }))
}

export function useExplorePatternsQuery(options: () => RestQueryOptions<ExplorePatternsQueryVariables>) {
  return useRestQuery('useExplorePatternsQuery', options, async (variables, signal): Promise<ExplorePatternsQuery> => ({
    tripPatterns: await explorePatterns(variables.feedCode, signal),
  }))
}

export function useFeedsQuery(options: () => { pollInterval?: number } = () => ({})) {
  return useRestQuery('useFeedsQuery', () => ({ ...options(), variables: {} }), async (_variables, signal): Promise<FeedsQuery> => ({
    feeds: await feedsWithActiveRevision(signal),
  }))
}

export function useAvlFeedsQuery(options: () => { pollInterval?: number } = () => ({})) {
  return useRestQuery('useAvlFeedsQuery', () => ({ ...options(), variables: {} }), async (_variables, signal): Promise<AvlFeedsQuery> => ({
    avlFeeds: await fetchAllPages((offset, limit) => AvlFeedCatalog.avlFeeds({ query: { offset, limit }, signal })) as AvlFeedsQuery['avlFeeds'],
  }))
}

export function useExploreCalendarQuery(options: () => RestQueryOptions<ExploreCalendarQueryVariables>) {
  return useRestQuery('useExploreCalendarQuery', options, async (variables, signal): Promise<ExploreCalendarQuery> => {
    const [calendars, calendarDates] = await Promise.all([
      fetchAllPages((offset, limit) => Gtfs.calendars({ path: { feedCode: variables.feedCode }, query: { offset, limit }, signal })),
      fetchAllPages((offset, limit) => Gtfs.calendarDates({ path: { feedCode: variables.feedCode }, query: { offset, limit }, signal })),
    ])
    return { calendars, calendarDates } as unknown as ExploreCalendarQuery
  })
}

export function useAgenciesDetailQuery(options: () => RestQueryOptions<AgenciesDetailQueryVariables>) {
  return useRestQuery('useAgenciesDetailQuery', options, async (variables, signal): Promise<AgenciesDetailQuery> => ({
    agencies: await fetchAllPages((offset, limit) => Gtfs.agencies({ path: { feedCode: variables.feedCode }, query: { offset, limit }, signal })) as AgenciesDetailQuery['agencies'],
  }))
}

export function useRoutesQuery(options: () => RestQueryOptions<RoutesQueryVariables>) {
  return useRestQuery('useRoutesQuery', options, async (variables, signal): Promise<RoutesQuery> => ({
    routes: await fetchAllPages((offset, limit) => Gtfs.routes({ path: { feedCode: variables.feedCode }, query: { offset, limit }, signal })) as RoutesQuery['routes'],
  }))
}

export function useStopsQuery(options: () => RestQueryOptions<StopsQueryVariables>) {
  return useRestQuery('useStopsQuery', options, async (variables, signal): Promise<StopsQuery> => ({
    stops: await fetchAllPages((offset, limit) => Gtfs.stops({ path: { feedCode: variables.feedCode }, query: { offset, limit }, signal })) as StopsQuery['stops'],
  }))
}

/** Same read (and cache entry) as [useAgenciesDetailQuery] — `/agencies` returns full rows either way — so a page using both issues one request. */
export function useAgenciesQuery(options: () => RestQueryOptions<AgenciesQueryVariables>): ReturnType<typeof useRestQuery<AgenciesQuery, AgenciesQueryVariables>> {
  return useAgenciesDetailQuery(options)
}
