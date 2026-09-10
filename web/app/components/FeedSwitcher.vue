<script setup lang="ts">
import {useFeeds} from '~/composables/useFeeds'
import {useDashboard} from '~/composables/useDashboard'

/**
 * Trigger for the feed drawer (see FeedDrawer.vue).
 * - default: a floating handle pinned to the right edge of the viewport.
 * - `block`: full-width labelled button for the mobile menu.
 */
withDefaults(defineProps<{ block?: boolean }>(), {block: false})

const {selectedFeed} = useFeeds()
const {isFeedDrawerOpen} = useDashboard()

function dotColor(f: {
  hasRealtime: boolean
  realtimeHealthy: boolean
}): 'success' | 'warning' | 'neutral' {
  if (!f.hasRealtime) return 'neutral'
  return f.realtimeHealthy ? 'success' : 'warning'
}
</script>

<template>
  <UButton
    v-if="block"
    color="neutral"
    variant="ghost"
    size="sm"
    block
    trailing-icon="i-lucide-chevron-right"
    :ui="{ trailingIcon: 'text-dimmed' }"
    @click="isFeedDrawerOpen = true"
  >
    <span class="flex items-center gap-2">
      <UChip v-if="selectedFeed" :color="dotColor(selectedFeed)" inset>
        <UIcon name="i-lucide-rss" class="size-4"/>
      </UChip>
      <span class="truncate">{{ selectedFeed?.name ?? 'Select feed' }}</span>
    </span>
  </UButton>

  <button
    v-else
    type="button"
    :title="`Feed: ${selectedFeed?.name ?? 'none'} (press F)`"
    aria-label="Switch feed"
    class="fixed right-0 top-1/4 z-100 flex -translate-y-1/2 flex-col items-center gap-2 rounded-l-lg border border-r-0 border-default bg-default/90 px-2 py-3 text-sm text-muted shadow-lg backdrop-blur transition-colors hover:bg-elevated hover:text-highlighted"
    @click="isFeedDrawerOpen = true"
  >
    <UChip :color="selectedFeed ? dotColor(selectedFeed) : 'neutral'" inset>
      <UIcon name="i-lucide-rss" class="size-5 shrink-0"/>
    </UChip>
    <span class="font-medium [writing-mode:vertical-rl]">Select feed</span>
  </button>
</template>
