import { defineQueryOptions } from '@pinia/colada'
import { Predictions, type PredictionAccuracySummary, type PredictionAlgorithm, type PredictionDayType, type PredictionTimeBand } from '~/api'
import { fetchAllPages } from '~/utils/restPagination'
import { avlKeys } from '../../keys/avl'

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
