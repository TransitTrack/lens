import { computed, ref } from 'vue'
import { feedContextKey } from '~/composables/feedContext'
import type { FeedContext } from '~/composables/feedContext'

export function feedContextStub(partial: Partial<FeedContext> = {}): FeedContext {
  const selectedFeedCode = partial.selectedFeedCode ?? ref<string | null>(null)
  return {
    feeds: ref([]),
    selectedFeedCode,
    selectedFeed: computed(() => null),
    selectedAvlFeedCode: computed(() => null),
    feedIsUnknown: computed(() => false),
    feedPath: (sub = '') => `/${selectedFeedCode.value ?? ''}${sub}`,
    avlFeeds: ref([]),
    avlFeedsError: ref<Error | null>(null),
    ...partial,
  }
}

/** `global` mount option providing a feed context, for `mountSuspended`. */
export function withFeedContext(partial: Partial<FeedContext> = {}) {
  return { global: { provide: { [feedContextKey as symbol]: feedContextStub(partial) } } }
}
