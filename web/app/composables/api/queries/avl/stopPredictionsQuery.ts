import { defineQueryOptions } from '@pinia/colada'
import { Predictions, type PredictionAlgorithm, type StopPrediction } from '~/api'
import { fetchAllPages } from '~/utils/restPagination'
import { avlKeys } from '../../keys/avl'

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
