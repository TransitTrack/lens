import { defineQueryOptions } from '@pinia/colada'
import { Predictions, type PredictionAlgorithm, type StopPrediction } from '~/api'
import { fetchAllPages } from '~/utils/restPagination'
import { avlKeys } from '../../keys/avl'

/** GET /feeds/{feedCode}/avl/vehicles/{vehicleId}/predictions — a vehicle's stop predictions (all pages). */
export const vehiclePredictionsQuery = defineQueryOptions((p: { avlFeedCode: string, vehicleId: string, algorithm?: PredictionAlgorithm }) => ({
  key: avlKeys.resource(p.avlFeedCode, 'vehicle-predictions', { vehicleId: p.vehicleId, algorithm: p.algorithm }),
  query: ({ signal }): Promise<StopPrediction[]> =>
    fetchAllPages((offset, limit) => Predictions.vehiclePredictions({
      path: { feedCode: p.avlFeedCode, vehicleId: p.vehicleId }, query: { algorithm: p.algorithm, offset, limit }, signal,
    })),
}))
