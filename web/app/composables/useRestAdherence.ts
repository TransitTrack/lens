import { Reports } from '~/api'
import type {
  AllRoutesAdherenceQuery, AllRoutesAdherenceQueryVariables,
  AllTripsAdherenceQuery, AllTripsAdherenceQueryVariables,
  RouteAdherenceQuery, RouteAdherenceQueryVariables,
  StopAdherenceQuery, StopAdherenceQueryVariables,
  TripAdherenceQuery, TripAdherenceQueryVariables,
} from '~/types/restViews'
import { useRestQuery, type RestQueryOptions } from './useRestQuery'
import { fetchAllPages } from '~/utils/restPagination'

export function useAllRoutesAdherenceQuery(options: () => RestQueryOptions<AllRoutesAdherenceQueryVariables>) {
  return useRestQuery('useAllRoutesAdherenceQuery', options, async (variables, signal): Promise<AllRoutesAdherenceQuery> => ({
    allRoutesAdherence: await fetchAllPages((offset, limit) => Reports.allRoutesAdherence({ path: { feedCode: variables.feedCode }, query: { startDate: variables.startDate, endDate: variables.endDate, offset, limit }, signal })) as AllRoutesAdherenceQuery['allRoutesAdherence'],
  }))
}

export function useAllTripsAdherenceQuery(options: () => RestQueryOptions<AllTripsAdherenceQueryVariables>) {
  return useRestQuery('useAllTripsAdherenceQuery', options, async (variables, signal): Promise<AllTripsAdherenceQuery> => ({
    allTripsAdherence: await fetchAllPages((offset, limit) => Reports.allTripsAdherence({ path: { feedCode: variables.feedCode }, query: { routeId: variables.routeId ?? undefined, startDate: variables.startDate, endDate: variables.endDate, offset, limit }, signal })) as AllTripsAdherenceQuery['allTripsAdherence'],
  }))
}

export function useRouteAdherenceQuery(options: () => RestQueryOptions<RouteAdherenceQueryVariables>) {
  return useRestQuery('useRouteAdherenceQuery', options, async (variables, signal): Promise<RouteAdherenceQuery> => ({
    routeAdherence: await fetchAllPages((offset, limit) => Reports.routeAdherence({ path: { feedCode: variables.feedCode, routeId: variables.routeId }, query: { startDate: variables.startDate, endDate: variables.endDate, offset, limit }, signal })) as RouteAdherenceQuery['routeAdherence'],
  }))
}

export function useTripAdherenceQuery(options: () => RestQueryOptions<TripAdherenceQueryVariables>) {
  return useRestQuery('useTripAdherenceQuery', options, async (variables, signal): Promise<TripAdherenceQuery> => ({
    tripAdherence: await fetchAllPages((offset, limit) => Reports.tripAdherence({ path: { feedCode: variables.feedCode, tripId: variables.tripId }, query: { startDate: variables.startDate, endDate: variables.endDate, offset, limit }, signal })) as TripAdherenceQuery['tripAdherence'],
  }))
}

export function useStopAdherenceQuery(options: () => RestQueryOptions<StopAdherenceQueryVariables>) {
  return useRestQuery('useStopAdherenceQuery', options, async (variables, signal): Promise<StopAdherenceQuery> => ({
    stopAdherence: await fetchAllPages((offset, limit) => Reports.stopAdherence({ path: { feedCode: variables.feedCode, stopId: variables.stopId }, query: { startDate: variables.startDate, endDate: variables.endDate, offset, limit }, signal })) as StopAdherenceQuery['stopAdherence'],
  }))
}
