import { ReportsApi } from '~~/generated/rest'
import type {
  AllRoutesAdherenceQuery, AllRoutesAdherenceQueryVariables,
  AllTripsAdherenceQuery, AllTripsAdherenceQueryVariables,
  RouteAdherenceQuery, RouteAdherenceQueryVariables,
  StopAdherenceQuery, StopAdherenceQueryVariables,
  TripAdherenceQuery, TripAdherenceQueryVariables,
} from '~/types/restViews'
import { useRestQuery, type RestQueryOptions } from './useRestQuery'
import { fetchAllPages } from '~/utils/restPagination'

const api = new ReportsApi()

export function useAllRoutesAdherenceQuery(options: () => RestQueryOptions<AllRoutesAdherenceQueryVariables>) {
  return useRestQuery(options, async (variables, signal): Promise<AllRoutesAdherenceQuery> => ({
    allRoutesAdherence: await fetchAllPages((offset, limit) => api.allRoutesAdherenceRaw({ ...variables, offset, limit }, { signal })) as AllRoutesAdherenceQuery['allRoutesAdherence'],
  }))
}

export function useAllTripsAdherenceQuery(options: () => RestQueryOptions<AllTripsAdherenceQueryVariables>) {
  return useRestQuery(options, async (variables, signal): Promise<AllTripsAdherenceQuery> => ({
    allTripsAdherence: await fetchAllPages((offset, limit) => api.allTripsAdherenceRaw({ ...variables, routeId: variables.routeId ?? undefined, offset, limit }, { signal })) as AllTripsAdherenceQuery['allTripsAdherence'],
  }))
}

export function useRouteAdherenceQuery(options: () => RestQueryOptions<RouteAdherenceQueryVariables>) {
  return useRestQuery(options, async (variables, signal): Promise<RouteAdherenceQuery> => ({
    routeAdherence: await fetchAllPages((offset, limit) => api.routeAdherenceRaw({ ...variables, offset, limit }, { signal })) as RouteAdherenceQuery['routeAdherence'],
  }))
}

export function useTripAdherenceQuery(options: () => RestQueryOptions<TripAdherenceQueryVariables>) {
  return useRestQuery(options, async (variables, signal): Promise<TripAdherenceQuery> => ({
    tripAdherence: await fetchAllPages((offset, limit) => api.tripAdherenceRaw({ ...variables, offset, limit }, { signal })) as TripAdherenceQuery['tripAdherence'],
  }))
}

export function useStopAdherenceQuery(options: () => RestQueryOptions<StopAdherenceQueryVariables>) {
  return useRestQuery(options, async (variables, signal): Promise<StopAdherenceQuery> => ({
    stopAdherence: await fetchAllPages((offset, limit) => api.stopAdherenceRaw({ ...variables, offset, limit }, { signal })) as StopAdherenceQuery['stopAdherence'],
  }))
}
