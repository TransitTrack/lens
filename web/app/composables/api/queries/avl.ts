import { defineQueryOptions } from '@pinia/colada'
import { Avl, type AvlFeed, AvlFeedCatalog, type AvlReport, type AvlReportCatalog, type AvlReportPage, type Headway, optional, type PredictionAccuracySummary, type PredictionAlgorithm, type PredictionDayType, Predictions, type PredictionTimeBand, Reports, type RouteAdherenceSummary, type StopPassage, type StopPrediction, type TripAdherenceSummary, type Vehicle, type VehicleTrail } from '~/api'
import { fetchAllPages } from '~/utils/restPagination'
import type { VehicleRow } from '~/types/vehicles'
import { avlKeys } from '../keys/avl'

/** GET /feeds/{feedCode}/avl/reports/route-adherence — per-route adherence summaries in a date range (all pages). */
export const allRoutesAdherenceQuery = defineQueryOptions((p: { feedCode: string, startDate: string, endDate: string }) => ({
  key: avlKeys.resource(p.feedCode, 'all-routes-adherence', { startDate: p.startDate, endDate: p.endDate }),
  query: ({ signal }): Promise<RouteAdherenceSummary[]> =>
    fetchAllPages((offset, limit) => Reports.allRoutesAdherence({
      path: { feedCode: p.feedCode },
      query: { startDate: p.startDate, endDate: p.endDate, offset, limit },
      signal,
    })),
}))

/** GET /feeds/{feedCode}/avl/reports/trip-adherence — per-trip adherence summaries in a date range, optionally for one route (all pages). */
export const allTripsAdherenceQuery = defineQueryOptions((p: { feedCode: string, routeId?: string, startDate: string, endDate: string }) => ({
  key: avlKeys.resource(p.feedCode, 'all-trips-adherence', { routeId: p.routeId, startDate: p.startDate, endDate: p.endDate }),
  query: ({ signal }): Promise<TripAdherenceSummary[]> =>
    fetchAllPages((offset, limit) => Reports.allTripsAdherence({
      path: { feedCode: p.feedCode },
      query: { routeId: p.routeId, startDate: p.startDate, endDate: p.endDate, offset, limit },
      signal,
    })),
}))

/** GET /avl-feeds — every AVL feed (all pages). Fresh for 60 s; polling is up to the caller. */
export const avlFeedsQuery = defineQueryOptions({
  key: avlKeys.feeds(),
  query: ({ signal }): Promise<AvlFeed[]> =>
    fetchAllPages((offset, limit) => AvlFeedCatalog.avlFeeds({ query: { offset, limit }, signal })),
  staleTime: 60_000,
})

/** GET /feeds/{feedCode}/avl/report-catalog — the vehicles and dates that have AVL reports. */
export const avlReportCatalogQuery = defineQueryOptions((p: { avlFeedCode: string }) => ({
  key: avlKeys.resource(p.avlFeedCode, 'report-catalog'),
  query: async ({ signal }): Promise<AvlReportCatalog> =>
    (await Avl.avlReportCatalog({ path: { feedCode: p.avlFeedCode }, signal })).data,
}))

/** GET /feeds/{feedCode}/avl/vehicles/{vehicleId}/report-page — ONE page of a vehicle's reports on purpose (server-paged by page / pageSize). */
export const avlReportPageQuery = defineQueryOptions((p: {
  avlFeedCode: string, vehicleId: string, date: string, startTime: string, endTime: string, page?: number, pageSize?: number
}) => ({
  key: avlKeys.resource(p.avlFeedCode, 'report-page', {
    vehicleId: p.vehicleId, date: p.date, startTime: p.startTime, endTime: p.endTime, page: p.page, pageSize: p.pageSize,
  }),
  query: async ({ signal }): Promise<AvlReportPage> =>
    (await Avl.avlReportPage({
      path: { feedCode: p.avlFeedCode, vehicleId: p.vehicleId },
      query: { date: p.date, startTime: p.startTime, endTime: p.endTime, page: p.page, pageSize: p.pageSize },
      signal,
    })).data,
}))

/** GET /feeds/{feedCode}/avl/vehicles/{vehicleId}/reports — a vehicle's newest raw AVL reports (one response). */
export const avlReportsQuery = defineQueryOptions((p: { avlFeedCode: string, vehicleId: string, limit?: number, since?: string }) => ({
  key: avlKeys.resource(p.avlFeedCode, 'reports', { vehicleId: p.vehicleId, limit: p.limit, since: p.since }),
  query: async ({ signal }): Promise<AvlReport[]> =>
    (await Avl.avlReports({ path: { feedCode: p.avlFeedCode, vehicleId: p.vehicleId }, query: { limit: p.limit, since: p.since }, signal })).data,
}))

/** GET /feeds/{feedCode}/avl/stops/{stopId}/headway — the predicted headway at a stop for a route. */
export const headwayQuery = defineQueryOptions((p: {
  avlFeedCode: string, stopId: string, routeId: string, directionId?: number, algorithm?: PredictionAlgorithm
}) => ({
  key: avlKeys.resource(p.avlFeedCode, 'headway', { stopId: p.stopId, routeId: p.routeId, directionId: p.directionId, algorithm: p.algorithm }),
  query: async ({ signal }): Promise<Headway> =>
    (await Predictions.headway({
      path: { feedCode: p.avlFeedCode, stopId: p.stopId },
      query: { routeId: p.routeId, directionId: p.directionId, algorithm: p.algorithm },
      signal,
    })).data,
}))

/**
 * GET /feeds/{feedCode}/vehicles — the map endpoint: every live vehicle in one response, with just the
 * trip / route / block / pattern fields the map and board draw. Raw fetch (not in the generated SDK).
 */
export const liveVehiclesQuery = defineQueryOptions((p: { feedCode: string }) => ({
  key: avlKeys.resource(p.feedCode, 'live-vehicles'),
  query: async ({ signal }): Promise<VehicleRow[]> => {
    const response = await fetch(`/api/v1/feeds/${encodeURIComponent(p.feedCode)}/vehicles`, { signal })
    if (!response.ok) throw new Error(`Vehicle request failed: ${response.status}`)
    const rows: unknown = await response.json()
    if (!Array.isArray(rows)) throw new Error('Invalid vehicle response')
    return rows as VehicleRow[]
  },
}))

/** GET /feeds/{feedCode}/avl/prediction-accuracy — prediction accuracy aggregates (all pages). */
export const predictionAccuracyQuery = defineQueryOptions((p: {
  avlFeedCode: string, algorithm?: PredictionAlgorithm, sinceDays?: number, routeId?: string, directionId?: number,
  dayType?: PredictionDayType, timeBand?: PredictionTimeBand
}) => ({
  key: avlKeys.resource(p.avlFeedCode, 'prediction-accuracy', {
    algorithm: p.algorithm, sinceDays: p.sinceDays, routeId: p.routeId, directionId: p.directionId, dayType: p.dayType, timeBand: p.timeBand,
  }),
  query: ({ signal }): Promise<PredictionAccuracySummary[]> =>
    fetchAllPages((offset, limit) => Predictions.predictionAccuracy({
      path: { feedCode: p.avlFeedCode },
      query: {
        algorithm: p.algorithm, sinceDays: p.sinceDays, routeId: p.routeId, directionId: p.directionId,
        dayType: p.dayType, timeBand: p.timeBand, offset, limit,
      },
      signal,
    })),
}))

/** GET /feeds/{feedCode}/avl/routes/{routeId}/adherence — a route's stop passages in a date range (all pages). */
export const routeAdherenceQuery = defineQueryOptions((p: { feedCode: string, routeId: string, startDate: string, endDate: string }) => ({
  key: avlKeys.resource(p.feedCode, 'route-adherence', { routeId: p.routeId, startDate: p.startDate, endDate: p.endDate }),
  query: ({ signal }): Promise<StopPassage[]> =>
    fetchAllPages((offset, limit) => Reports.routeAdherence({
      path: { feedCode: p.feedCode, routeId: p.routeId },
      query: { startDate: p.startDate, endDate: p.endDate, offset, limit },
      signal,
    })),
}))

/** GET /feeds/{feedCode}/avl/stops/{stopId}/adherence — a stop's passages in a date range (all pages). */
export const stopAdherenceQuery = defineQueryOptions((p: { feedCode: string, stopId: string, startDate: string, endDate: string }) => ({
  key: avlKeys.resource(p.feedCode, 'stop-adherence', { stopId: p.stopId, startDate: p.startDate, endDate: p.endDate }),
  query: ({ signal }): Promise<StopPassage[]> =>
    fetchAllPages((offset, limit) => Reports.stopAdherence({
      path: { feedCode: p.feedCode, stopId: p.stopId },
      query: { startDate: p.startDate, endDate: p.endDate, offset, limit },
      signal,
    })),
}))

/** GET /feeds/{feedCode}/avl/stops/{stopId}/predictions — a stop's upcoming arrivals (all pages). */
export const stopPredictionsQuery = defineQueryOptions((p: {
  avlFeedCode: string, stopId: string, routeId?: string, directionId?: number, algorithm?: PredictionAlgorithm
}) => ({
  key: avlKeys.resource(p.avlFeedCode, 'stop-predictions', { stopId: p.stopId, routeId: p.routeId, directionId: p.directionId, algorithm: p.algorithm }),
  query: ({ signal }): Promise<StopPrediction[]> =>
    fetchAllPages((offset, limit) => Predictions.stopPredictions({
      path: { feedCode: p.avlFeedCode, stopId: p.stopId },
      query: { routeId: p.routeId, directionId: p.directionId, algorithm: p.algorithm, offset, limit },
      signal,
    })),
}))

/** GET /feeds/{feedCode}/avl/trips/{tripId}/adherence — a trip's stop passages in a date range (all pages). */
export const tripAdherenceQuery = defineQueryOptions((p: { feedCode: string, tripId: string, startDate: string, endDate: string }) => ({
  key: avlKeys.resource(p.feedCode, 'trip-adherence', { tripId: p.tripId, startDate: p.startDate, endDate: p.endDate }),
  query: ({ signal }): Promise<StopPassage[]> =>
    fetchAllPages((offset, limit) => Reports.tripAdherence({
      path: { feedCode: p.feedCode, tripId: p.tripId },
      query: { startDate: p.startDate, endDate: p.endDate, offset, limit },
      signal,
    })),
}))

/** GET /feeds/{feedCode}/avl/vehicles/{vehicleId}/predictions — a vehicle's stop predictions (all pages). */
export const vehiclePredictionsQuery = defineQueryOptions((p: { avlFeedCode: string, vehicleId: string, algorithm?: PredictionAlgorithm }) => ({
  key: avlKeys.resource(p.avlFeedCode, 'vehicle-predictions', { vehicleId: p.vehicleId, algorithm: p.algorithm }),
  query: ({ signal }): Promise<StopPrediction[]> =>
    fetchAllPages((offset, limit) => Predictions.vehiclePredictions({
      path: { feedCode: p.avlFeedCode, vehicleId: p.vehicleId }, query: { algorithm: p.algorithm, offset, limit }, signal,
    })),
}))

/** GET /feeds/{feedCode}/avl/vehicles/{vehicleId} — one live vehicle, or null when unknown. */
export const vehicleQuery = defineQueryOptions((p: { avlFeedCode: string, vehicleId: string }) => ({
  key: avlKeys.resource(p.avlFeedCode, 'vehicles', { vehicleId: p.vehicleId }),
  query: ({ signal }): Promise<Vehicle | null> =>
    optional(Avl.vehicle({ path: { feedCode: p.avlFeedCode, vehicleId: p.vehicleId }, signal })),
}))

/** GET /feeds/{feedCode}/avl/vehicles/{vehicleId}/trail — a vehicle's recent movement, summarised by the backend. */
export const vehicleTrailQuery = defineQueryOptions((p: { avlFeedCode: string, vehicleId: string, limit?: number }) => ({
  key: avlKeys.resource(p.avlFeedCode, 'trail', { vehicleId: p.vehicleId, limit: p.limit }),
  query: async ({ signal }): Promise<VehicleTrail> =>
    (await Avl.vehicleTrail({ path: { feedCode: p.avlFeedCode, vehicleId: p.vehicleId }, query: { limit: p.limit }, signal })).data,
}))
