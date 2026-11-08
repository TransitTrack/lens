import { defineQueryOptions } from '@pinia/colada'
import { Gtfs, optional, type Shape } from '~/api'
import { feedKeys, type Rev } from '../../keys/feeds'
import { cachePolicy, revisionParam } from '../cachePolicy'

/** GET /feeds/{feedCode}/shapes/{shapeId} — one Shape, or null when unknown. */
export const shapeQuery = defineQueryOptions((p: Rev & { shapeId: string }) => ({
  key: feedKeys.resource(p, 'shapes', { shapeId: p.shapeId }),
  query: ({ signal }): Promise<Shape | null> =>
    optional(Gtfs.shape({ path: { feedCode: p.feedCode, shapeId: p.shapeId }, query: { revisionId: revisionParam(p) }, signal })),
  ...cachePolicy(p),
}))
