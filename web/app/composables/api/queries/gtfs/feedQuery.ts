import { defineQueryOptions } from '@pinia/colada'
import { Gtfs, optional, type Feed } from '~/api'
import { feedKeys } from '../../keys/feeds'

/** GET /feeds/{code} — one feed, or null when unknown. */
export const feedQuery = defineQueryOptions((p: { code: string }) => ({
  key: feedKeys.feed(p.code),
  query: ({ signal }): Promise<Feed | null> =>
    optional(Gtfs.feed({ path: { code: p.code }, signal })),
}))
