import { useFeedContext } from './feedContext'
import type { AnnotatedFeed } from '~/utils/joins/feeds'

export type { AnnotatedFeed }

/**
 * Feed selection is URL-driven: the `feedCode` route param is the selected GTFS
 * feed. The default layout resolves it against the feed lists and provides the
 * result; this reads it.
 */
export function useFeeds() {
  return useFeedContext()
}
