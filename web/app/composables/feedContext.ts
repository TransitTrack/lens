import type { ComputedRef, InjectionKey, Ref } from 'vue'
import type { AvlFeed } from '~/api'
import type { AnnotatedFeed } from '~/utils/joins/feeds'

/** The layout's feed data, provided once and read by pages and components below it. */
export interface FeedContext {
  feeds: Ref<AnnotatedFeed[]> | ComputedRef<AnnotatedFeed[]>
  selectedFeedCode: Ref<string | null> | ComputedRef<string | null>
  selectedFeed: Ref<AnnotatedFeed | null> | ComputedRef<AnnotatedFeed | null>
  selectedAvlFeedCode: Ref<string | null> | ComputedRef<string | null>
  feedIsUnknown: Ref<boolean> | ComputedRef<boolean>
  feedPath: (sub?: string) => string
  avlFeeds: Ref<AvlFeed[]> | ComputedRef<AvlFeed[]>
  avlFeedsError: Ref<Error | null> | ComputedRef<Error | null>
}

export const feedContextKey: InjectionKey<FeedContext> = Symbol('FeedContext')

export function provideFeedContext(ctx: FeedContext): void {
  provide(feedContextKey, ctx)
}

export function useFeedContext(): FeedContext {
  const ctx = inject(feedContextKey, null)
  if (!ctx) throw new Error('FeedContext not provided')
  return ctx
}
