import { defineQueryOptions } from '@pinia/colada'
import { Predictions, type Headway, type PredictionAlgorithm } from '~/api'
import { avlKeys } from '../../keys/avl'

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
