import { defineQueryOptions } from '@pinia/colada'
import { Avl, type AvlReportCatalog } from '~/api'
import { avlKeys } from '../../keys/avl'

/** GET /feeds/{feedCode}/avl/report-catalog — the vehicles and dates that have AVL reports. */
export const avlReportCatalogQuery = defineQueryOptions((p: { avlFeedCode: string }) => ({
  key: avlKeys.resource(p.avlFeedCode, 'report-catalog'),
  query: async ({ signal }): Promise<AvlReportCatalog> =>
    (await Avl.avlReportCatalog({ path: { feedCode: p.avlFeedCode }, signal })).data,
}))
